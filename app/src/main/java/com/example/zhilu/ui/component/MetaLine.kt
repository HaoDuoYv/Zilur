package com.example.zhilu.ui.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.example.zhilu.ui.theme.LocalExtendedColors
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 元信息行：把「时间 · 计数 · 标签」这类零散拼接统一成一处，消灭各页手拼字符串。
 */
@Composable
fun MetaLine(
    parts: List<String>,
    modifier: Modifier = Modifier,
    color: Color = LocalExtendedColors.current.inkFaint
) {
    val text = parts.filter { it.isNotBlank() }.joinToString(" · ")
    if (text.isEmpty()) return
    Text(
        text = text,
        style = ZhiLuType.meta,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}