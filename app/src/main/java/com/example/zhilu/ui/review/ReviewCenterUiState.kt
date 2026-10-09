package com.example.zhilu.ui.review

import com.example.zhilu.domain.model.ReminderBucket
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderWithContext
import com.example.zhilu.domain.model.ReviewHeatmap
import com.example.zhilu.domain.model.ReviewQueue
import com.example.zhilu.domain.model.ReviewStats
import com.example.zhilu.domain.model.TodoWithContext
import com.example.zhilu.domain.reminder.ReviewSchedulePolicy
import com.example.zhilu.ui.reminder.ReminderFilter

/** 复习中心的三个档位。 */
enum class ReviewTab(val label: String) {
    Pending("待复习"),
    Todos("待办"),
    Reminders("提醒")
}

/** 「待办」档的三个子标签（对齐产品原型；三者互斥，数字由界面拼在标签上）。 */
enum class TodoSubTab(val label: String) {
    Pending("待处理"),
    Overdue("已逾期"),
    Completed("已完成")
}

/**
 * 复习中心页面状态。
 *
 * 「提醒」档沿用老提醒中心的三档筛选（[reminderFilter]），
 * 分类结果 [reminderBucket] 里只有提醒本体，展示所需的标题/待办文本/档位
 * 从 [reminderContextById] 按提醒 id 取（两个 Flow 分开收集，避免分类器感知展示字段）。
 */
data class ReviewCenterUiState(
    val selectedTab: ReviewTab = ReviewTab.Pending,
    val isLoading: Boolean = true,
    val error: String? = null,
    /** 今天 0 点（复习是天粒度，行内「逾期 N 天 / N 天后」按它算）。 */
    val startOfToday: Long = 0L,
    /**
     * 当前用户阶梯的档位总数 —— 「第 N/M 次」与进度点个数都用它。
     *
     * 跟设置里的自定义间隔走（见 `ReviewIntervals`）；界面别再写死 `/5`。
     */
    val stepCount: Int = ReviewSchedulePolicy.defaultStepCount,
    // ---- 「待复习」档 ----
    val queue: ReviewQueue = ReviewQueue(),
    /**
     * 近一周曲线 + 评价分布（窗口按今天 0 点推满 `ReviewStats.WINDOW_DAYS` 天）。
     *
     * 「今天复习了几篇」由 [ReviewStats.todayCount] 给出（曲线的最后一格就是今天），
     * 所以这里不再单存一个今日计数 —— 同一个数字打两条查询迟早会在跨零点时对不上。
     */
    val stats: ReviewStats = ReviewStats(),
    /**
     * 复习热力图（近 [ReviewHeatmap.WEEKS] 周，列 = 周、行 = 星期）。
     *
     * 与 [stats] 同源（今天 0 点口径），但窗口更长；[ReviewHeatmap.todayIndex]
     * 之后的格子是「未来」，界面画成空格。
     */
    val heatmap: ReviewHeatmap = ReviewHeatmap(),
    // ---- 「待办」档 ----
    /** 待办档当前子标签（待处理 / 已逾期 / 已完成）。 */
    val todoSubTab: TodoSubTab = TodoSubTab.Pending,
    /**
     * 跨笔记的全部待办（含已完成）。
     *
     * 排序来自 DAO（未完成 → 带提醒 → 时间）；「已完成」小组在 [completedTodos]
     * 里按完成时间倒序 —— 与 DAO 的排序键不同，不硬塞进同一条 SQL。
     */
    val todos: List<TodoWithContext> = emptyList(),
    // ---- 「提醒」档 ----
    val reminderFilter: ReminderFilter = ReminderFilter.Pending,
    val reminderBucket: ReminderBucket = ReminderBucket(),
    val reminderContextById: Map<Long, ReminderWithContext> = emptyMap()
) {
    /** 未完成的全部待办（「待处理 + 已逾期」的并集；DAO 已把带提醒的排在前面）。 */
    val pendingTodos: List<TodoWithContext>
        get() = todos.filterNot { it.todo.isCompleted }

    /**
     * 「已逾期」：未完成且提醒时间早于**今天 0 点**。
     *
     * 逾期按天判定、与复习分区同口径 —— 今天内刚过点的提醒不算逾期，
     * 它留在「待处理」里（DAO 已把它排在最前），不该被挪进另一个标签下藏起来。
     */
    val overdueTodos: List<TodoWithContext>
        get() = pendingTodos.filter { it.isOverdue(startOfToday) }

    /** 「待处理」：未完成且未逾期。 */
    val onTimeTodos: List<TodoWithContext>
        get() = pendingTodos.filterNot { it.isOverdue(startOfToday) }

    /** 待办档的「已完成」区：最近完成的在前。 */
    val completedTodos: List<TodoWithContext>
        get() = todos.filter { it.todo.isCompleted }.sortedByDescending { it.todo.completedAt }

    /** 待办档计数（分段标题上的「待办 N」）：未完成的条数。 */
    val pendingTodoCount: Int
        get() = pendingTodos.size

    /** 当前子标签下要展示的待办列表。 */
    val visibleTodos: List<TodoWithContext>
        get() = when (todoSubTab) {
            TodoSubTab.Pending -> onTimeTodos
            TodoSubTab.Overdue -> overdueTodos
            TodoSubTab.Completed -> completedTodos
        }

    /** 提醒档三档筛选下的当前列表（待处理 = 今天 + 未来，与老提醒中心同口径）。 */
    val filteredReminders: List<ReminderInstance>
        get() = when (reminderFilter) {
            ReminderFilter.Pending -> (reminderBucket.today + reminderBucket.future).sortedBy { it.dueAt }
            ReminderFilter.Overdue -> reminderBucket.overdue
            ReminderFilter.Completed -> reminderBucket.completed
        }

    /** 提醒档是否有任何提醒（含逾期与已完成）。 */
    val hasAnyReminder: Boolean
        get() = reminderBucket.today.isNotEmpty() ||
            reminderBucket.future.isNotEmpty() ||
            reminderBucket.overdue.isNotEmpty() ||
            reminderBucket.completed.isNotEmpty()

    /** 分段标题上的「提醒 N」：未完成（待处理 + 逾期）的提醒数。 */
    val pendingReminderCount: Int
        get() = reminderBucket.today.size + reminderBucket.future.size + reminderBucket.overdue.size

    /**
     * 毕业率的分子 / 分母：已完成的计划数、计划总数（进行中 + 已暂停 + 已完成）。
     *
     * 直接从队列算，不再多打一条聚合查询 —— 队列本来就把这三种状态分好区了。
     * 分母为 0 时界面不显示毕业率（没有分母的比率是假话）。
     */
    val graduatedPlanCount: Int get() = queue.completed.size

    val totalPlanCount: Int
        get() = queue.completed.size + queue.paused.size + queue.queuedCount
}

/**
 * 待办是否逾期：有提醒时间、且已早于今天 0 点（天粒度判定，见 [ReviewCenterUiState.overdueTodos]）。
 * 没有提醒时间的待办永远不会逾期。
 */
internal fun TodoWithContext.isOverdue(startOfToday: Long): Boolean =
    !todo.isCompleted && todo.remindAt?.let { it < startOfToday } == true
