package com.example.zhilu.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.ReviewPlanWithNote
import com.example.zhilu.domain.model.TodoWithContext
import com.example.zhilu.ui.component.AnimatedListItem
import com.example.zhilu.ui.component.AppEmptyState
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.component.SectionHeader
import com.example.zhilu.ui.component.SegmentedToggle
import com.example.zhilu.ui.component.ZhiLuDivider
import com.example.zhilu.ui.navigation.AppTabScaffold
import com.example.zhilu.ui.navigation.Destination
import com.example.zhilu.ui.navigation.LocalAppIntents
import com.example.zhilu.ui.navigation.LocalAppSnackbar
import com.example.zhilu.ui.navigation.navigateTopLevel
import com.example.zhilu.ui.reminder.ReminderFilter
import com.example.zhilu.ui.reminder.ReminderRow
import com.example.zhilu.ui.reminder.reminderStatusStyle
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

private val PaneTogglePadding = PaddingValues(
    start = Spacing.PageGutter,
    end = Spacing.PageGutter,
    top = Spacing.Sm,
    bottom = Spacing.Xs
)

/**
 * 复习中心：底栏第二格的顶层页，三档 —— 「待复习」（计划与队列）/「待办」（跨笔记待办）/「提醒」（提醒实例）。
 *
 * 视觉对齐产品原型：一级三档下是卡片化的内容（热力图卡 + 统计格 + 计划卡 /
 * 任务卡 + 子标签），不再是无卡片的数据行列表。卡片外壳全部走 [com.example.zhilu.ui.component.AppCard]
 * 或同源的轻量壳，动森外观自动换材质，**不写分主题的两套**。
 *
 * 档位不进路由：铃铛 / 通知 / 设置页入口的一次性意图走 [LocalAppIntents]
 * （带参数的路由每次都是新 entry，会重置本页 ViewModel）。
 */
@Composable
fun ReviewCenterScreen(
    navController: NavHostController,
    viewModel: ReviewCenterViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = LocalAppSnackbar.current
    val intents = LocalAppIntents.current

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val openNote = { noteId: Long ->
        navController.navigate(Destination.NoteEdit.createRoute(noteId))
    }
    val startReview = { noteId: Long ->
        // 「开始复习」= 打开笔记 + 自动弹出复习面板；意图走进程级交接（见 AppIntents）。
        intents.requestReviewSheet(noteId)
        navController.navigate(Destination.NoteEdit.createRoute(noteId))
    }
    val goNotes = { navController.navigateTopLevel(Destination.Home) }

    AppTabScaffold(
        topBar = { AppTopBar(title = "复习中心") }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            SegmentedToggle(
                options = listOf(
                    ReviewTab.Pending to "${ReviewTab.Pending.label} ${state.queue.queuedCount}",
                    ReviewTab.Todos to "${ReviewTab.Todos.label} ${state.pendingTodoCount}",
                    ReviewTab.Reminders to "${ReviewTab.Reminders.label} ${state.pendingReminderCount}"
                ),
                selected = state.selectedTab,
                onSelect = viewModel::selectTab,
                modifier = Modifier.padding(PaneTogglePadding)
            )
            when (state.selectedTab) {
                ReviewTab.Pending -> PendingPane(
                    state = state,
                    viewModel = viewModel,
                    onOpenNote = openNote,
                    onStartReview = startReview,
                    onGoNotes = goNotes
                )
                ReviewTab.Todos -> TodosPane(
                    state = state,
                    viewModel = viewModel,
                    onOpenNote = openNote,
                    onGoNotes = goNotes
                )
                ReviewTab.Reminders -> RemindersPane(
                    state = state,
                    viewModel = viewModel,
                    onOpenNote = openNote,
                    onGoReviewQueue = { viewModel.selectTab(ReviewTab.Pending) },
                    onGoNotes = goNotes
                )
            }
        }
    }
}

// ---- 「待复习」档 ----

@Composable
private fun PendingPane(
    state: ReviewCenterUiState,
    viewModel: ReviewCenterViewModel,
    onOpenNote: (Long) -> Unit,
    onStartReview: (Long) -> Unit,
    onGoNotes: () -> Unit
) {
    val queue = state.queue
    if (!state.isLoading && !queue.hasAnyPlan) {
        AppEmptyState(
            onAction = onGoNotes,
            icon = Icons.Outlined.Style,
            title = "还没有复习计划",
            description = "在任意笔记顶栏点「复习」即可开启计划，到期后会回到这里。",
            buttonText = "去笔记列表"
        )
        return
    }

    var archivedExpanded by rememberSaveable { mutableStateOf(false) }
    val pausedCount = queue.paused.size + queue.completed.size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.Xl)
    ) {
        item(key = "review-heatmap") {
            ReviewHeatmapCard(
                heatmap = state.heatmap,
                modifier = Modifier.padding(top = Spacing.Xs)
            )
        }
        item(key = "review-stats") {
            ReviewStatsGrid(
                stats = state.stats,
                graduatedPlanCount = state.graduatedPlanCount,
                totalPlanCount = state.totalPlanCount,
                modifier = Modifier.padding(top = Spacing.Xs)
            )
        }

        // 逾期置顶，然后今天、接下来（7 天内 + 更远，远期计划不能"消失"）。
        planSection("已逾期", queue.overdue, ReviewPlanZone.Overdue, state, viewModel, onStartReview, onOpenNote)
        planSection("今天", queue.today, ReviewPlanZone.Today, state, viewModel, onStartReview, onOpenNote)
        planSection(
            "接下来",
            queue.upcoming + queue.later,
            ReviewPlanZone.Upcoming,
            state,
            viewModel,
            onStartReview,
            onOpenNote
        )

        if (pausedCount > 0) {
            item(key = "review-archived-header") {
                // 折叠区默认收起，带计数；展开状态是纯 UI 瞬时态（rememberSaveable 足够）。
                SectionHeader(
                    title = "已暂停 / 已完成 · $pausedCount",
                    modifier = Modifier.clickable { archivedExpanded = !archivedExpanded },
                    trailing = {
                        Icon(
                            imageVector = if (archivedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (archivedExpanded) "收起" else "展开",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
            if (archivedExpanded) {
                planRows(
                    items = queue.paused,
                    zone = ReviewPlanZone.Paused,
                    state = state,
                    onStartReview = onStartReview,
                    onOpenNote = onOpenNote,
                    onPause = viewModel::pausePlan,
                    onResume = viewModel::resumePlan,
                    onRestart = { viewModel.restartPlan(it) }
                )
                planRows(
                    items = queue.completed,
                    zone = ReviewPlanZone.Completed,
                    state = state,
                    onStartReview = onStartReview,
                    onOpenNote = onOpenNote,
                    onPause = viewModel::pausePlan,
                    onResume = viewModel::resumePlan,
                    onRestart = { viewModel.restartPlan(it) }
                )
            }
        }
    }
}

private fun LazyListScope.planSection(
    title: String,
    items: List<ReviewPlanWithNote>,
    zone: ReviewPlanZone,
    state: ReviewCenterUiState,
    viewModel: ReviewCenterViewModel,
    onStartReview: (Long) -> Unit,
    onOpenNote: (Long) -> Unit
) {
    if (items.isEmpty()) return
    item(key = "review-header-${zone.name}") {
        SectionHeader("$title · ${items.size}")
    }
    planRows(
        items = items,
        zone = zone,
        state = state,
        onStartReview = onStartReview,
        onOpenNote = onOpenNote,
        onPause = viewModel::pausePlan,
        onResume = viewModel::resumePlan,
        onRestart = { viewModel.restartPlan(it) }
    )
}

private fun LazyListScope.planRows(
    items: List<ReviewPlanWithNote>,
    zone: ReviewPlanZone,
    state: ReviewCenterUiState,
    onStartReview: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
    onPause: (ReviewPlan) -> Unit,
    onResume: (ReviewPlan) -> Unit,
    onRestart: (Long) -> Unit
) {
    itemsIndexed(
        items = items,
        key = { _, item -> "review-plan-${item.plan.id}" }
    ) { index, item ->
        AnimatedListItem(index = index) {
            ReviewPlanCard(
                item = item,
                zone = zone,
                // 档位总数跟用户阶梯走（自定义间隔后写死的 /5 会说谎）
                stepTotal = state.stepCount,
                startOfToday = state.startOfToday,
                onOpen = { onOpenNote(item.plan.noteId) },
                onStart = { onStartReview(item.plan.noteId) },
                onPause = { onPause(item.plan) },
                onResume = { onResume(item.plan) },
                onRestart = { onRestart(item.plan.noteId) }
            )
        }
    }
}

// ---- 「待办」档 ----

/**
 * 待办档：跨笔记汇总全部待办，按「待处理 / 已逾期 / 已完成」三个子标签切换。
 *
 * 与「提醒」档的分工：这里管**待办本身**（完成 / 恢复 / 删除），
 * 提醒档管**提醒实例**（完成提醒会回写到这里同一条待办）——
 * 两边写的是同一份数据，任何一边操作后另一边都会随 Flow 刷新。
 *
 * 子标签的列表内容来自 UiState 的派生（`visibleTodos`），逾期口径
 * （提醒时间早于今天 0 点）也在那里，本层只管排版与动作。
 */
@Composable
private fun TodosPane(
    state: ReviewCenterUiState,
    viewModel: ReviewCenterViewModel,
    onOpenNote: (Long) -> Unit,
    onGoNotes: () -> Unit
) {
    if (!state.isLoading && state.todos.isEmpty()) {
        AppEmptyState(
            onAction = onGoNotes,
            icon = Icons.Outlined.Checklist,
            title = "还没有待办",
            description = "在笔记里添加待办事项（也可以让助手帮你建），它们会汇总到这里。",
            buttonText = "去笔记列表"
        )
        return
    }

    // 删除不可逆（待办没有回收站），走一次确认；完成 / 恢复都可撤销，不弹窗。
    var pendingDelete by remember { mutableStateOf<TodoWithContext?>(null) }
    val items = state.visibleTodos

    Column(modifier = Modifier.fillMaxSize()) {
        ReviewSubTabs(
            options = listOf(
                TodoSubTab.Pending to "${TodoSubTab.Pending.label} ${state.onTimeTodos.size}",
                TodoSubTab.Overdue to "${TodoSubTab.Overdue.label} ${state.overdueTodos.size}",
                TodoSubTab.Completed to "${TodoSubTab.Completed.label} ${state.completedTodos.size}"
            ),
            selected = state.todoSubTab,
            onSelect = viewModel::selectTodoSubTab,
            modifier = Modifier.padding(PaneTogglePadding)
        )

        if (items.isEmpty()) {
            TodoEmptyState(
                subTab = state.todoSubTab,
                onGoNotes = onGoNotes,
                onBackToPending = { viewModel.selectTodoSubTab(TodoSubTab.Pending) }
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Spacing.Xl, top = Spacing.Xs)
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> "todo-${item.todo.id}" }
            ) { index, item ->
                AnimatedListItem(index = index) {
                    TodoTaskCard(
                        item = item,
                        startOfToday = state.startOfToday,
                        onClick = { item.todo.noteId?.let(onOpenNote) },
                        onComplete = { viewModel.completeTodo(item.todo) },
                        onReopen = { viewModel.reopenTodo(item.todo) },
                        onDelete = { pendingDelete = item }
                    )
                }
            }
        }
    }

    pendingDelete?.let { item ->
        TodoDeleteDialog(
            todo = item.todo,
            onConfirm = {
                viewModel.deleteTodo(item.todo)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

/** 子标签下的局部空态：文案随子标签变，按钮把用户送回「待处理」。 */
@Composable
private fun TodoEmptyState(
    subTab: TodoSubTab,
    onGoNotes: () -> Unit,
    onBackToPending: () -> Unit
) {
    when (subTab) {
        TodoSubTab.Pending -> AppEmptyState(
            onAction = onGoNotes,
            icon = Icons.Outlined.Checklist,
            title = "太棒了，没有待处理的待办",
            description = "新添加的待办会汇总到这里。",
            buttonText = "去笔记列表",
            compact = true
        )
        TodoSubTab.Overdue -> AppEmptyState(
            onAction = onBackToPending,
            icon = Icons.Outlined.Checklist,
            title = "没有逾期的待办",
            description = "昨天及更早的提醒都已处理完。",
            buttonText = "看待处理",
            compact = true
        )
        TodoSubTab.Completed -> AppEmptyState(
            onAction = onBackToPending,
            icon = Icons.Outlined.Checklist,
            title = "还没有完成的待办",
            description = "完成一条待办后会回到这里归档。",
            buttonText = "看待处理",
            compact = true
        )
    }
}

// ---- 「提醒」档 ----

@Composable
private fun RemindersPane(
    state: ReviewCenterUiState,
    viewModel: ReviewCenterViewModel,
    onOpenNote: (Long) -> Unit,
    onGoReviewQueue: () -> Unit,
    onGoNotes: () -> Unit
) {
    if (!state.isLoading && !state.hasAnyReminder) {
        AppEmptyState(
            onAction = onGoNotes,
            icon = Icons.Outlined.NotificationsNone,
            title = "没有提醒",
            description = "为笔记设置复习或待办提醒，它们会按时出现在这里。",
            buttonText = "去笔记列表"
        )
        return
    }

    val filter = state.reminderFilter
    val style = reminderStatusStyle(filter, MaterialTheme.colorScheme)
    val items = state.filteredReminders

    Column(modifier = Modifier.fillMaxSize()) {
        ReviewSubTabs(
            options = listOf(
                ReminderFilter.Pending to "${ReminderFilter.Pending.label} ${state.pendingReminderCount}",
                ReminderFilter.Overdue to "${ReminderFilter.Overdue.label} ${state.reminderBucket.overdue.size}",
                ReminderFilter.Completed to "${ReminderFilter.Completed.label} ${state.reminderBucket.completed.size}"
            ),
            selected = filter,
            onSelect = viewModel::selectReminderFilter,
            modifier = Modifier.padding(PaneTogglePadding)
        )
        if (items.isEmpty()) {
            AppEmptyState(
                onAction = { viewModel.selectReminderFilter(ReminderFilter.Pending) },
                icon = Icons.Outlined.NotificationsNone,
                title = "暂无${filter.label}提醒",
                description = "切换上方标签查看其它状态的提醒。",
                buttonText = "看待处理",
                compact = true
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Spacing.Xl, top = Spacing.Xs)
        ) {
            itemsIndexed(
                items = items,
                key = { _, reminder -> "reminder-${filter.name}-${reminder.id}" }
            ) { index, reminder ->
                AnimatedListItem(index = index) {
                    Column {
                        ReminderRow(
                            reminder = reminder,
                            style = style,
                            statusLabel = filter.label,
                            context = state.reminderContextById[reminder.id],
                            stepTotal = state.stepCount,
                            actionsEnabled = filter != ReminderFilter.Completed,
                            onClick = { reminder.noteId?.let(onOpenNote) },
                            onDone = { viewModel.completeReminder(reminder) },
                            onCancel = { viewModel.cancelReminder(reminder) },
                            onSnooze = { option -> viewModel.snoozeReminder(reminder, option) },
                            onReviewQueue = onGoReviewQueue
                        )
                        if (index < items.lastIndex) {
                            ZhiLuDivider(modifier = Modifier.padding(start = Spacing.PageGutter))
                        }
                    }
                }
            }
        }
    }
}
