package com.example.zhilu.ui.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderType
import com.example.zhilu.domain.model.ReminderWithContext
import com.example.zhilu.ui.component.DocumentRow
import com.example.zhilu.ui.component.MetaLine
import com.example.zhilu.ui.component.QuietAction
import com.example.zhilu.ui.theme.ShapeTokens
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PillShape = RoundedCornerShape(ShapeTokens.Pill)

/**
 * 提醒行：左侧状态色通高书脊 + "要做什么" + 上下文 + 状态胶囊 + 操作区。
 *
 * 信息层级：主标题回答"这件事是什么"（待办 → 待办文本；复习 → 笔记标题），
 * meta 行回答"哪篇笔记 · 什么时候"（老版本只有到期时间与"关联笔记"四个字，
 * 看不出是哪篇、要干什么）。
 *
 * 操作按类型分叉（单一事实来源）：
 * - **待办提醒**：完成（回写 `todo_items.completedAt`）/ 取消（清 `remindAt`）/ 延后；
 * - **复习提醒**：只读 —— 计划自己管提醒的生灭，这里只给去「待复习」的引导。
 *
 * @param stepTotal 复习阶梯档位总数（「第 N/M 次」的 M）；跟用户自定义间隔走，由调用方从状态传入。
 */
@Composable
fun ReminderRow(
    reminder: ReminderInstance,
    style: ReminderStatusStyle,
    statusLabel: String,
    context: ReminderWithContext?,
    stepTotal: Int,
    actionsEnabled: Boolean,
    onClick: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onSnooze: (SnoozeOption) -> Unit,
    onReviewQueue: () -> Unit
) {
    val isReview = reminder.type == ReminderType.REVIEW
    val noteTitle = context?.noteTitle
    val todoContent = context?.todoContent?.takeIf { it.isNotBlank() }

    val title = if (isReview) {
        noteTitle ?: "复习计划"
    } else {
        todoContent ?: noteTitle ?: "待办提醒"
    }
    val metaParts = buildList {
        // REVIEW 行的标题已经是笔记标题，档位单独成一段
        if (isReview) {
            context?.reviewStep?.let { add("第 ${it + 1}/$stepTotal 次复习") }
        }
        // 标题已经吃掉笔记标题时不再重复一遍（复习行、待办文本为空的待办行都会走到这里）
        noteTitle?.takeIf { it != title }?.let { add(it) }
        add(formatDueTime(reminder.dueAt))
    }

    DocumentRow(
        accent = style.accent,
        onClick = when {
            isReview -> onReviewQueue
            reminder.noteId != null -> onClick
            else -> null
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = ZhiLuType.rowTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                MetaLine(
                    parts = metaParts,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            StatusCapsule(text = statusLabel, container = style.container, content = style.onContainer)
        }
        if (actionsEnabled) {
            if (isReview) {
                Text(
                    text = "由复习计划驱动，点这里去「待复习」处理",
                    style = ZhiLuType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.Sm)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.Sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onDone, shape = PillShape) {
                        Text("完成", style = ZhiLuType.chip)
                    }
                    QuietAction(label = "取消", onClick = onCancel)
                    SnoozeMenu(onSnooze = onSnooze)
                }
            }
        }
    }
}

/** 「延后」下拉：两个快捷时间（1 小时后 / 明天上午）。 */
@Composable
private fun SnoozeMenu(onSnooze: (SnoozeOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        QuietAction(label = "延后", onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SnoozeOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, style = ZhiLuType.bodySmall) },
                    onClick = {
                        expanded = false
                        onSnooze(option)
                    }
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
