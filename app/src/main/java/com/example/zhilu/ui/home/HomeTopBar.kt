package com.example.zhilu.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.component.AppIconButton
import com.example.zhilu.ui.component.AppTopBar

/**
 * 笔记页顶栏：品牌名 + 提醒中心入口。
 * 存在到期提醒时铃铛右上角点一枚小圆点，用视觉而非文案表达「有事」。
 */
@Composable
fun HomeTopBar(
    dueReminderCount: Int,
    onOpenReminders: () -> Unit
) {
    AppTopBar(
        title = "知录",
        actions = {
            Box {
                AppIconButton(
                    icon = Icons.Default.Notifications,
                    contentDescription = if (dueReminderCount > 0) {
                        "提醒中心，$dueReminderCount 项到期"
                    } else {
                        "提醒中心"
                    },
                    onClick = onOpenReminders
                )
                if (dueReminderCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 8.dp)
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                    )
                }
            }
        }
    )
}