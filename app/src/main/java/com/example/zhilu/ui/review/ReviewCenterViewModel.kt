package com.example.zhilu.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.datastore.UserPreferences
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReviewHeatmap
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.ReviewStats
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.domain.reminder.ReminderClassifier
import com.example.zhilu.domain.reminder.ReviewQueueClassifier
import com.example.zhilu.domain.reminder.TodoReminderSync
import com.example.zhilu.domain.repository.ReminderRepository
import com.example.zhilu.domain.repository.ReviewRepository
import com.example.zhilu.domain.repository.TodoRepository
import com.example.zhilu.domain.usecase.ManageReviewPlanUseCase
import com.example.zhilu.domain.usecase.ResolveReminderUseCase
import com.example.zhilu.ui.navigation.AppIntents
import com.example.zhilu.ui.reminder.ReminderFilter
import com.example.zhilu.ui.reminder.SnoozeOption
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 复习中心：一档管"计划与队列"（待复习），一档管"跨笔记待办"（待办），
 * 一档管"提醒实例"（提醒）。
 *
 * 数据来源是三条独立 Flow（`review_plans JOIN notes` / `todo_items JOIN notes` /
 * `reminder_instances JOIN …`），分别分类后进同一个 UiState；提醒行的展示上下文
 * 按提醒 id 建映射，供 UI 取用。列表动作全部**不做本地乐观更新**：
 * 写库成功即由 Room Flow 回流，手写一份"移动后的列表"只会与回流数据打架。
 */
@HiltViewModel
class ReviewCenterViewModel(
    private val reviewRepository: ReviewRepository,
    private val reminderRepository: ReminderRepository,
    private val todoRepository: TodoRepository,
    private val todoReminderSync: TodoReminderSync,
    private val manageReviewPlan: ManageReviewPlanUseCase,
    private val resolveReminder: ResolveReminderUseCase,
    private val appIntents: AppIntents,
    private val userPreferences: UserPreferences,
    private val queueClassifier: ReviewQueueClassifier = ReviewQueueClassifier(),
    private val reminderClassifier: ReminderClassifier = ReminderClassifier(),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val startOfTodayProvider: () -> Long = { defaultStartOfToday() }
) : ViewModel() {

    @Inject
    constructor(
        reviewRepository: ReviewRepository,
        reminderRepository: ReminderRepository,
        todoRepository: TodoRepository,
        todoReminderSync: TodoReminderSync,
        manageReviewPlan: ManageReviewPlanUseCase,
        resolveReminder: ResolveReminderUseCase,
        appIntents: AppIntents,
        userPreferences: UserPreferences
    ) : this(
        reviewRepository = reviewRepository,
        reminderRepository = reminderRepository,
        todoRepository = todoRepository,
        todoReminderSync = todoReminderSync,
        manageReviewPlan = manageReviewPlan,
        resolveReminder = resolveReminder,
        appIntents = appIntents,
        userPreferences = userPreferences,
        // 必须显式给出第 9 个参数：本辅助构造与主构造前 8 参签名相同，
        // 少传一参时 `this(...)` 会解析回自己，触发 "cycle in the delegation calls chain"。
        queueClassifier = ReviewQueueClassifier()
    )

    private val _uiState = MutableStateFlow(ReviewCenterUiState())
    val uiState: StateFlow<ReviewCenterUiState> = _uiState.asStateFlow()

    init {
        observePlans()
        observeStats()
        observeHeatmap()
        observeTodos()
        observeReminders()
        observeIntervals()
        observeIntents()
    }

    fun selectTab(tab: ReviewTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun selectTodoSubTab(subTab: TodoSubTab) {
        _uiState.update { it.copy(todoSubTab = subTab) }
    }

    fun selectReminderFilter(filter: ReminderFilter) {
        _uiState.update { it.copy(reminderFilter = filter) }
    }

    // ---- 「待复习」档动作（计划与提醒成对配平，见 ManageReviewPlanUseCase）----

    /** 暂停：保留档位，之后可「继续」。 */
    fun pausePlan(plan: ReviewPlan) {
        launchAction { manageReviewPlan.pause(plan) }
    }

    /** 继续：保留档位、立即回到待复习队列。 */
    fun resumePlan(plan: ReviewPlan) {
        launchAction { manageReviewPlan.resume(plan, nowProvider()) }
    }

    /** 重新开始：档位归零（「已完成」的计划拾起）。 */
    fun restartPlan(noteId: Long) {
        launchAction { manageReviewPlan.restart(noteId, nowProvider()) }
    }

    // ---- 「待办」档动作（源头写 `todo_items`，提醒经 TodoReminderSync 配平）----

    /**
     * 勾选完成：**先写源头** `todo_items.completedAt`，再让 TODO 提醒随之结束。
     * 与笔记页勾选是同一条路径的两种入口（顺序不可反：反了会留下"未完成却已提醒完"的残留）。
     */
    fun completeTodo(todo: TodoItem) {
        launchAction {
            when (val result = todoRepository.completeTodo(todo.id, nowProvider())) {
                is RepositoryResult.Success -> todoReminderSync.markDone(todo.id)
                is RepositoryResult.Error -> result
            }
        }
    }

    /** 恢复未完成：清 `completedAt`，提醒按当前状态重新配平（有 `remindAt` 则重新挂上）。 */
    fun reopenTodo(todo: TodoItem) {
        val reopened = todo.copy(completedAt = null)
        launchAction {
            when (val result = todoRepository.updateTodo(reopened)) {
                is RepositoryResult.Success -> todoReminderSync.reconcile(reopened)
                is RepositoryResult.Error -> result
            }
        }
    }

    /** 删除待办：**先取消提醒再删本体** —— 反序若中途失败会留下"提醒指向已删待办"的孤儿。 */
    fun deleteTodo(todo: TodoItem) {
        launchAction {
            when (val result = todoReminderSync.cancel(todo.id)) {
                is RepositoryResult.Success -> todoRepository.deleteTodo(todo.id)
                is RepositoryResult.Error -> result
            }
        }
    }

    // ---- 「提醒」档动作（TODO 类回写源头，REVIEW 类 UI 层已禁掉）----

    fun completeReminder(reminder: ReminderInstance) {
        launchAction { resolveReminder.complete(reminder, nowProvider()) }
    }

    fun cancelReminder(reminder: ReminderInstance) {
        launchAction { resolveReminder.cancel(reminder, nowProvider()) }
    }

    fun snoozeReminder(reminder: ReminderInstance, option: SnoozeOption) {
        val dueAt = when (option) {
            SnoozeOption.OneHourLater -> nowProvider() + HOUR
            SnoozeOption.TomorrowMorning -> startOfTodayProvider() + DAY + MORNING_HOUR
        }
        launchAction { resolveReminder.snooze(reminder, dueAt, nowProvider()) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * 统一的"执行 + 错误上浮"：动作成功不需要拼 UI（Flow 会回流），只处理失败。
     */
    private fun launchAction(block: suspend () -> RepositoryResult<*>) {
        viewModelScope.launch {
            val result = block()
            if (result is RepositoryResult.Error) {
                _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    private fun observePlans() {
        viewModelScope.launch {
            reviewRepository.observePlans().collect { result ->
                when (result) {
                    is RepositoryResult.Success -> _uiState.update {
                        it.copy(
                            queue = queueClassifier.classify(result.data, startOfTodayProvider()),
                            startOfToday = startOfTodayProvider(),
                            isLoading = false,
                            error = null
                        )
                    }
                    is RepositoryResult.Error -> _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
            }
        }
    }

    /**
     * 近一周曲线 + 评价分布。
     *
     * 窗口起点按**今天 0 点**推满整周，与列表的分区口径同一个基准
     * （`ReviewQueueClassifier` 也是以今天 0 点为界）—— 两处若各算各的，
     * 曲线最后一格和队列的「今天」会在跨零点时对不上。
     */
    private fun observeStats() {
        val windowStart = ReviewStats.windowStart(startOfTodayProvider())
        viewModelScope.launch {
            reviewRepository.observeStats(windowStart).collect { stats ->
                _uiState.update { it.copy(stats = stats) }
            }
        }
    }

    /**
     * 热力图（近 [ReviewHeatmap.WEEKS] 周，列 = 周、行 = 星期）。
     *
     * 查询起点与「今天格位」从 [ReviewHeatmap.window] **一次**算出 ——
     * 分开算第二次就可能跨零点让网格的未来格与数据分桶错位。
     */
    private fun observeHeatmap() {
        val window = ReviewHeatmap.window(startOfTodayProvider(), ZoneId.systemDefault())
        viewModelScope.launch {
            reviewRepository.observeHeatmap(window.start, window.todayIndex).collect { heatmap ->
                _uiState.update { it.copy(heatmap = heatmap) }
            }
        }
    }

    /**
     * 跨笔记待办（全局「待办」档）。
     *
     * 排序在 DAO 定稿（未完成 → 带提醒 → 时间），这里只原样入状态；
     * 「已完成」小组倒序由 UiState 的派生属性完成。
     */
    private fun observeTodos() {
        viewModelScope.launch {
            todoRepository.observeAllWithContext().collect { result ->
                when (result) {
                    is RepositoryResult.Success -> _uiState.update {
                        it.copy(todos = result.data, isLoading = false, error = null)
                    }
                    is RepositoryResult.Error -> _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
            }
        }
    }

    private fun observeReminders() {
        viewModelScope.launch {
            reminderRepository.observeAllWithContext().collect { result ->
                when (result) {
                    is RepositoryResult.Success -> {
                        val contexts = result.data
                        val bucket = reminderClassifier.classify(
                            reminders = contexts.map { it.reminder },
                            now = nowProvider(),
                            startOfToday = startOfTodayProvider()
                        )
                        _uiState.update {
                            it.copy(
                                reminderBucket = bucket,
                                reminderContextById = contexts.associateBy { ctx -> ctx.reminder.id },
                                isLoading = false,
                                error = null
                            )
                        }
                    }
                    is RepositoryResult.Error -> _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
            }
        }
    }

    /**
     * 用户自定义复习间隔（档位总数进状态）。
     *
     * 「第 N/M 次」与进度点个数必须跟**当前**阶梯走 —— 用户把 5 档改成 3 档后，
     * 写死的 `/5` 就成了假话。间隔本身由仓库在写库时读取（见 `ReviewRepositoryImpl`），
     * 这里只负责显示口径。
     */
    private fun observeIntervals() {
        viewModelScope.launch {
            userPreferences.reviewIntervals.collect { days ->
                _uiState.update { it.copy(stepCount = days.size) }
            }
        }
    }

    /**
     * 消费「这次进来要看哪一档」的一次性意图（铃铛 / 通知 / 设置页入口）。
     *
     * 用 collect 而不是 init 里读一次：复习中心是顶层页，`restoreState` 会复用
     * 同一个 ViewModel —— 读一次只能覆盖"VM 首次创建"那一回，之后从铃铛进来就失灵了。
     */
    private fun observeIntents() {
        viewModelScope.launch {
            appIntents.pendingReviewTab.collect { pending ->
                if (pending != null) {
                    appIntents.consumeReviewTab()
                    _uiState.update { it.copy(selectedTab = pending) }
                }
            }
        }
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L

        /** 「明天上午」的落点：上午 9 点整。 */
        const val MORNING_HOUR = 9 * HOUR

        fun defaultStartOfToday(): Long =
            LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
    }
}
