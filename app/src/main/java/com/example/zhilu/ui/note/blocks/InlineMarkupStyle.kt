package com.example.zhilu.ui.note.blocks

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import com.example.zhilu.data.datastore.ThemePalette
import com.example.zhilu.domain.markup.InlineSpan
import com.example.zhilu.ui.theme.inlineToneSpanStyle

/**
 * 给纯文本套上标记样式。
 *
 * 编辑框用它 —— 编辑态**不**解析 `**` / 反引号 / `$`，那些保持源码可见
 * （设计文档 §3.7 行为约定）。只读路径走 `RichText`，那里还要额外处理公式与加粗，
 * 所以那条路是另一种写法，但样式函数（[inlineToneSpanStyle]）共用。
 */
fun buildToneStyledText(
    text: String,
    spans: List<InlineSpan>,
    darkTheme: Boolean,
    accessible: Boolean = false,
    /** 语义标记的色值跟着外观走（见 `ThemePalettes.kt` 的 `toneBlocks`）。 */
    palette: ThemePalette = ThemePalette.DEFAULT
): AnnotatedString {
    if (spans.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var cursor = 0
        for (span in spans) {
            val start = span.start.coerceIn(0, text.length)
            val end = span.end.coerceIn(0, text.length)
            if (end <= start) continue
            if (start > cursor) append(text.substring(cursor, start))
            pushStyle(inlineToneSpanStyle(span.tone, span.brush, darkTheme, accessible, palette))
            append(text.substring(start, end))
            pop()
            cursor = end
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}
