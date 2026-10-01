package com.example.zhilu.ui.reminder

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/** 提醒中心的状态分段。 */
enum class ReminderFilter(val label: String) {
    Pending("待处理"),
    Overdue("已逾期"),
    Completed("已完成")
}

/** 单个提醒分段的视觉规格。 */
data class ReminderStatusStyle(
    val accent: Color,
    val container: Color,
    val onContainer: Color
)

/**
 * 由主题色派生的状态配色（纯函数，便于测试）。
 */
fun reminderStatusStyle(filter: ReminderFilter, scheme: ColorScheme): ReminderStatusStyle = when (filter) {
    ReminderFilter.Pending -> ReminderStatusStyle(
        accent = scheme.primary,
        container = scheme.primaryContainer,
        onContainer = scheme.onPrimaryContainer
    )
    ReminderFilter.Overdue -> ReminderStatusStyle(
        accent = scheme.error,
        container = scheme.errorContainer,
        onContainer = scheme.onErrorContainer
    )
    ReminderFilter.Completed -> ReminderStatusStyle(
        accent = scheme.tertiary,
        container = scheme.secondaryContainer,
        onContainer = scheme.onSecondaryContainer
    )
}