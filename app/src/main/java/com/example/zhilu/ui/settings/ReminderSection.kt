package com.example.zhilu.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.example.zhilu.ui.component.AppSwitch
import androidx.compose.runtime.Composable

/** 提醒：复习中心入口 + 通知权限 + 总开关。 */
@Composable
fun ReminderSection(
    notificationPermissionGranted: Boolean,
    remindersEnabled: Boolean,
    onOpenReminders: () -> Unit,
    onOpenSystemNotificationSettings: () -> Unit,
    onToggleReminders: (Boolean) -> Unit
) {
    SettingsGroup(title = "提醒") {
        SettingsRow(
            title = "复习中心",
            description = "复习计划与提醒（待处理 / 已逾期 / 已完成）",
            leadingIcon = Icons.Outlined.NotificationsNone,
            onClick = onOpenReminders,
            trailing = {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        SettingsRow(
            title = "通知权限",
            description = if (notificationPermissionGranted) "已开启" else "未开启，点击前往系统设置",
            leadingIcon = Icons.Outlined.NotificationsActive,
            onClick = onOpenSystemNotificationSettings,
            trailing = {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        SettingsRow(
            title = "提醒总开关",
            description = if (remindersEnabled) {
                "开启后按计划检查并发送提醒"
            } else {
                "已关闭，不再检查或发送提醒"
            },
            leadingIcon = Icons.Outlined.Alarm,
            trailing = {
                AppSwitch(
                    checked = remindersEnabled,
                    onCheckedChange = onToggleReminders
                )
            }
        )
    }
}