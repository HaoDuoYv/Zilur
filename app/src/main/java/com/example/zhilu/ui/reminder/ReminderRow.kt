package com.example.zhilu.ui.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.example.zhilu.ui.component.AppCard
import com.example.zhilu.ui.component.DescBlock
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
 * 提醒卡：知识点标题 + 状态标记 + 信息行 +（复习）引导块 /（待办）操作行。
 *
 * 形态与其他两档（待复习 [com.example.zhilu.ui.review.ReviewPlanCard]、
 * 待办 [com.example.zhilu.ui.review.TodoTaskCard]）一致 —— [AppCard] 卡片壳，
 * 外侧间距与双主题材质（纸墨阴影 / 动森描边 + 暖褐投影 + 大圆角）由它统一处理，
 * 不再走通铺的 `DocumentRow`（那会让提醒档成为三档里唯一的"另一套形状"）。
 *
 * 信息层级：主标题回答"这件事是什么"（待办 → 待办文本；复习 → 笔记标题），
 * meta 行回答"哪篇笔记 · 什么时候"。
 *
 * 操作按类型分叉（单一事实来源）：
 * - **待办提醒**：完成（回写 `todo_items.completedAt`）/ 取消（清 `remindAt`）/ 延后；
 * - **复习提醒**：只读 —— 计划自己管提醒的生灭，引导语收进
 *   [DescBlock]（原型 `task-card-desc` 的形态，与待办卡的来源块同一实现）。
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

    AppCard(
        onClick = when {
            isReview -> onReviewQueue
            reminder.noteId != null -> onClick
            else -> null
        }
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = title,
                style = ZhiLuType.rowTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            StatusCapsule(text = statusLabel, container = style.container, content = style.onContainer)
        }
        MetaLine(
            parts = metaParts,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (actionsEnabled) {
            if (isReview) {
                DescBlock("由复习计划驱动，点这里去「待复习」处理")
            } else {
                Row(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .height(32.dp),
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
