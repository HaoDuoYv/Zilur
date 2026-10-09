package com.example.zhilu.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.TodoWithContext
import com.example.zhilu.ui.component.AppCard
import com.example.zhilu.ui.component.DescBlock
import com.example.zhilu.ui.component.QuietAction
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.Radius
import com.example.zhilu.ui.theme.ShapeTokens
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.palettePaint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PillShape = RoundedCornerShape(ShapeTokens.Pill)

/**
 * 待办任务卡（对齐产品原型的 task-card 形态）：
 * 标题 + 状态标签 / 提醒时间 / 来源说明块 / 完成与删除操作。
 *
 * 状态用**标签 + 操作文案**表达（与子标签一一对应），不再只靠删除线 ——
 * 子标签切换后「已完成」和「已逾期」可能同屏出现，删除线一色两义会读混。
 * 已完成卡的形态也照原型：底色转淡、无阴影、虚线描边、标题划线。
 *
 * 打开笔记只在 [TodoWithContext.noteAlive] 时可用：回收站里的笔记
 * 点进去是打不开的编辑页，宁可不可点，也别把用户送进死路。
 */
@Composable
fun TodoTaskCard(
    item: TodoWithContext,
    startOfToday: Long,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    onReopen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val todo = item.todo
    val completed = todo.isCompleted
    val overdue = item.isOverdue(startOfToday)
    val scheme = MaterialTheme.colorScheme

    val status = when {
        completed -> TodoStatus.Done
        overdue -> TodoStatus.Overdue
        else -> TodoStatus.Pending
    }

    if (completed) {
        // 已完成卡照原型的形态单独成壳：底色转淡、无投影、虚线描边。
        CompletedCardShell(
            modifier = modifier,
            onClick = onClick.takeIf { item.noteAlive }
        ) {
            TodoCardBody(item = item, status = status, completed = true, onComplete = onComplete, onReopen = onReopen, onDelete = onDelete)
        }
    } else {
        AppCard(
            modifier = modifier,
            onClick = onClick.takeIf { item.noteAlive },
            contentPadding = PaddingValues(Spacing.CardPadding)
        ) {
            TodoCardBody(item = item, status = status, completed = false, onComplete = onComplete, onReopen = onReopen, onDelete = onDelete)
        }
    }
}

/** 卡片正文（两种外壳共用的内容）。 */
@Composable
private fun ColumnScope.TodoCardBody(
    item: TodoWithContext,
    status: TodoStatus,
    completed: Boolean,
    onComplete: () -> Unit,
    onReopen: () -> Unit,
    onDelete: () -> Unit
) {
    val todo = item.todo
    val scheme = MaterialTheme.colorScheme

    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = todo.content,
            style = ZhiLuType.rowTitle,
            color = if (completed) scheme.onSurfaceVariant else scheme.onSurface,
            textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(10.dp))
        StatusTag(status)
    }

    todo.remindAt?.let { remindAt ->
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = formatRemindTime(remindAt),
                style = ZhiLuType.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }

    // 附带色条的说明块（原型 task-card-desc）：说明这条待办的来源。
    when {
        item.noteAlive -> DescBlock(item.noteTitle?.ifBlank { "未命名知识" } ?: "未命名知识")
        todo.noteId != null -> DescBlock("笔记已删除")
        else -> Unit // 独立待办（无来源笔记），不添噪
    }

    ActionRow {
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

/**
 * 「已完成」卡的轻量外壳：淡底、无投影、**虚线描边**（原型 `border: 1px dashed`）。
 *
 * 不再复用 [AppCard]：那一层的描边/投影是它自己按外观选好的，虚线得画在
 * 「卡片本体」的边界上，挂在传入 modifier 上会连外间距一起圈进去。
 * 圆角仍跟两套外观的卡片半径走（动森 20 / 纸墨 14），只是换一种边线。
 */
@Composable
private fun CompletedCardShell(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    val paint = palettePaint(LocalThemePalette.current)
    val isAnimal = paint.componentBorder != Color.Unspecified
    val shape = RoundedCornerShape(if (isAnimal) Radius.CardAnimalIsland else Radius.Card)
    val outline = MaterialTheme.colorScheme.outline
    val base = modifier
        .fillMaxWidth()
        .padding(
            PaddingValues(
                horizontal = Spacing.PageGutter,
                vertical = Spacing.CardGap / 2
            )
        )
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
        .dashedOutline(outline.copy(alpha = 0.65f), shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    Column(modifier = base.padding(PaddingValues(Spacing.CardPadding)), content = content)
}

/** 圆角虚线描边：沿卡片边界内缩半线宽，虚线不会在边缘被裁成半根。 */
private fun Modifier.dashedOutline(color: Color, shape: RoundedCornerShape): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    val inset = stroke / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(shape.topStart.toPx(size, this), shape.topStart.toPx(size, this)),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f)))
    )
}

/** 待办的三态（与子标签一一对应，决定状态标签的文案与语义色）。 */
private enum class TodoStatus(val label: String) {
    Pending("待处理"),
    Overdue("已逾期"),
    Done("已完成")
}

/** 状态标签：浅底语义色小胶囊（待处理 primary / 已逾期 error / 已完成中性）。 */
@Composable
private fun StatusTag(status: TodoStatus) {
    val scheme = MaterialTheme.colorScheme
    val (background, foreground) = when (status) {
        TodoStatus.Pending -> scheme.primary.copy(alpha = 0.14f) to scheme.primary
        TodoStatus.Overdue -> scheme.error.copy(alpha = 0.14f) to scheme.error
        TodoStatus.Done -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    Surface(shape = CircleShape, color = background) {
        Text(
            text = status.label,
            style = ZhiLuType.label,
            color = foreground,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** 卡片底部的操作行。 */
@Composable
private fun ActionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .height(32.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

private fun formatRemindTime(remindAt: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(remindAt))
