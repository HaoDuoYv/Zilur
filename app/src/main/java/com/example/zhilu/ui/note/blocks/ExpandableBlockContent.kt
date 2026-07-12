package com.example.zhilu.ui.note.blocks

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * 可折叠的长文本块组件。
 *
 * 当文本实际行数超过 [maxLines] 时，默认折叠显示前 N 行，并在底部显示「展开更多」按钮；
 * 点击后完整显示，按钮变为「收起」。折叠状态为运行时状态，不持久化。
 */
@Composable
fun ExpandableTextContent(
    text: String,
    maxLines: Int = 6,
    modifier: Modifier = Modifier
) {
    ExpandableText(
        text = text.ifBlank { " " },
        maxLines = maxLines,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

/**
 * 通用的可折叠文本组件，允许调用方自定义文本样式。
 */
@Composable
internal fun ExpandableText(
    text: String,
    maxLines: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember(text) { mutableStateOf(false) }
    var hasOverflow by remember(text) { mutableStateOf(false) }

    Column(
        modifier = modifier.animateContentSize()
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = if (isExpanded) Int.MAX_VALUE else maxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!isExpanded) {
                    hasOverflow = result.hasVisualOverflow
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (hasOverflow || isExpanded) {
            TextButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = if (isExpanded) "收起" else "展开更多",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
