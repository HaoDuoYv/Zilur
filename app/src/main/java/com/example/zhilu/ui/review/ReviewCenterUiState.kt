package com.example.zhilu.ui.review

import com.example.zhilu.domain.model.ReminderBucket
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderWithContext
import com.example.zhilu.domain.model.ReviewQueue
import com.example.zhilu.ui.reminder.ReminderFilter

/** 复习中心的两个档位。 */
enum class ReviewTab(val label: String) {
    Pending("待复习"),
    Reminders("提醒")
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
    // ---- 「待复习」档 ----
    val queue: ReviewQueue = ReviewQueue(),
    val reviewedTodayCount: Int = 0,
    // ---- 「提醒」档 ----
    val reminderFilter: ReminderFilter = ReminderFilter.Pending,
    val reminderBucket: ReminderBucket = ReminderBucket(),
    val reminderContextById: Map<Long, ReminderWithContext> = emptyMap()
) {
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
}
