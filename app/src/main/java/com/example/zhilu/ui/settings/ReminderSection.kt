package com.example.zhilu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.reminder.ReviewIntervals
import com.example.zhilu.ui.component.AppSwitch
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 「学习与提醒」组：复习中心入口 + 复习间隔 + 提醒总开关（+ 未授权时的通知权限）。
 *
 * 与早先的「提醒」组相比有三处结构调整：
 * 1. **复习间隔并入本组** —— 复习中心与复习间隔本是同一件事，此前被拆在「提醒」与
 *    「复习」两个分组里，用户得在两处找"复习"；
 * 2. **通知权限按需隐藏** —— 已授权时这一行不再出现，只有没授权时才作为提示行出现；
 * 3. **复习中心带「待处理 N」角标** —— 高频入口把进度外显，不进去也知道有没有欠账。
 */
@Composable
fun ReminderSection(
    notificationPermissionGranted: Boolean,
    remindersEnabled: Boolean,
    reviewIntervals: List<Long>,
    reviewPendingCount: Int,
    onOpenReminders: () -> Unit,
    onOpenReviewInterval: () -> Unit,
    onOpenSystemNotificationSettings: () -> Unit,
    onToggleReminders: (Boolean) -> Unit
) {
    SettingsGroup(title = "学习与提醒") {
        SettingsRow(
            title = "复习中心",
            leadingIcon = Icons.Outlined.NotificationsNone,
            onClick = onOpenReminders,
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)
                ) {
                    // 计数为 0 时不画角标 —— "待处理 0"是噪音，不是信息
                    if (reviewPendingCount > 0) {
                        PendingBadge(count = reviewPendingCount)
                    }
                    SettingsChevron()
                }
            }
        )
        SettingsRow(
            title = "复习间隔",
            leadingIcon = Icons.Outlined.Schedule,
            onClick = onOpenReviewInterval,
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)
                ) {
                    SettingsRowValue(ReviewIntervals.summary(reviewIntervals))
                    SettingsChevron()
                }
            }
        )
        SettingsRow(
            title = "提醒总开关",
            leadingIcon = Icons.Outlined.Alarm,
            trailing = {
                AppSwitch(
                    checked = remindersEnabled,
                    onCheckedChange = onToggleReminders
                )
            }
        )
        // 已授权即隐藏：能隐藏则隐藏，只在真正需要用户去系统设置时才占一行。
        if (!notificationPermissionGranted) {
            SettingsRow(
                title = "通知权限",
                description = "未开启，点击前往系统设置",
                leadingIcon = Icons.Outlined.NotificationsActive,
                onClick = onOpenSystemNotificationSettings,
                trailing = { SettingsChevron() }
            )
        }
    }
}

/** 复习中心的待处理角标：主色容器色胶囊，弱提示不抢标题。 */
@Composable
private fun PendingBadge(count: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = "待处理 $count",
            style = ZhiLuType.meta,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = Spacing.Sm, vertical = 2.dp)
        )
    }
}
