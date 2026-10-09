package com.example.zhilu.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.TodoWithContext
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
 * 待办行：色书脊 + 待办文本 + 上下文（来源笔记 · 提醒时间）+ 操作区。
 *
 * 状态不另起胶囊 —— 行所在的区块（待处理 / 已完成）已经说明了状态，
 * 再挂一个胶囊是同一句话说两遍。状态改由**书脊色**与**删除线**表达
 * （与笔记里的勾选效果同一语义：完成 = 划线 + 弱化）。
 *
 * 打开笔记只在 [TodoWithContext.noteAlive] 时可用：回收站里的笔记
 * 点进去是打不开的编辑页，宁可不可点，也别把用户送进死路。
 */
@Composable
fun TodoRow(
    item: TodoWithContext,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    onReopen: () -> Unit,
    onDelete: () -> Unit
) {
    val todo = item.todo
    val completed = todo.isCompleted
    val scheme = MaterialTheme.colorScheme
    val accent = if (completed) scheme.tertiary else scheme.primary

    val metaParts = buildList {
        when {
            item.noteAlive -> add(item.noteTitle?.ifBlank { "未命名知识" } ?: "未命名知识")
            todo.noteId == null -> Unit // 独立待办（无来源笔记），不添噪
            else -> add("笔记已删除")
        }
        todo.remindAt?.let { add(formatRemindTime(it)) }
    }

    DocumentRow(
        accent = accent,
        onClick = onClick.takeIf { item.noteAlive }
    ) {
        Text(
            text = todo.content,
            style = ZhiLuType.rowTitle,
            color = if (completed) scheme.onSurfaceVariant else scheme.onSurface,
            textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        MetaLine(
            parts = metaParts,
            modifier = Modifier.padding(top = 2.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (completed) {
                QuietAction(label = "恢复", onClick = onReopen)
            } else {
                OutlinedButton(onClick = onComplete, shape = PillShape) {
                    Text("完成", style = ZhiLuType.chip)
                }
            }
            QuietAction(label = "删除", onClick = onDelete)
        }
    }
}

private fun formatRemindTime(remindAt: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(remindAt))
