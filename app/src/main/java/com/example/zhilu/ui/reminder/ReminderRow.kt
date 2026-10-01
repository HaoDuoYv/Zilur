package com.example.zhilu.ui.reminder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.ui.component.DocumentRow
import com.example.zhilu.ui.component.MetaLine
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 提醒行：左侧状态色通高书脊 + 到期时间 + 关联信息 + 状态胶囊 + 完成/取消。
 * 书脊颜色即状态（待处理 / 已逾期 / 已完成），与笔记列表共用同一套文档行结构。
 */
@Composable
fun ReminderRow(
    reminder: ReminderInstance,
    style: ReminderStatusStyle,
    statusLabel: String,
    actionsEnabled: Boolean,
    onClick: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    DocumentRow(
        accent = style.accent,
        onClick = if (reminder.noteId != null) onClick else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatDueTime(reminder.dueAt),
                    style = ZhiLuType.rowTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                MetaLine(
                    parts = listOf(
                        if (reminder.noteId != null) "关联笔记" else "未关联笔记"
                    ),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            StatusCapsule(text = statusLabel, container = style.container, content = style.onContainer)
            if (actionsEnabled) {
                OutlinedButton(
                    onClick = onDone,
                    shape = RoundedCornerShape(percent = 50)
                ) {
                    Text("完成", style = ZhiLuType.chip)
                }
                Text(
                    text = "取消",
                    style = ZhiLuType.chip,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusCapsule(text: String, container: Color, content: Color) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = container,
        contentColor = content
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = ZhiLuType.label
        )
    }
}

private fun formatDueTime(dueAt: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(dueAt))