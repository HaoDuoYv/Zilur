package com.example.zhilu.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.note.latex.LatexImage
import com.example.zhilu.ui.note.latex.rememberLatexImage

/**
 * AI 消息富文本渲染：支持代码块（``` 围栏）、块级公式（$$...$$）、
 * 标题（#/##/###）、无序/有序列表、引用（>）、行内 **粗体**、`行内代码`、$行内公式$。
 * 流式输出期间未闭合的围栏/公式按普通文本显示，不会吞字。
 */

private enum class SegmentType { TEXT, CODE, LATEX }

private data class Segment(val type: SegmentType, val content: String, val language: String = "")

private val codeFenceRegex = Regex("```([\\w+#-]*)[ \\t]*\\n?([\\s\\S]*?)```")
private val displayLatexRegex = Regex("\\$\\$([\\s\\S]+?)\\$\\$")
private val orderedListRegex = Regex("^(\\d+\\.) (.*)")

private fun parseSegments(text: String): List<Segment> {
    val raw = mutableListOf<Segment>()
    var rest = text
    while (true) {
        val match = codeFenceRegex.find(rest)
        if (match == null) {
            if (rest.isNotEmpty()) raw.add(Segment(SegmentType.TEXT, rest))
            break
        }
        if (match.range.first > 0) {
            raw.add(Segment(SegmentType.TEXT, rest.substring(0, match.range.first)))
        }
        raw.add(
            Segment(
                type = SegmentType.CODE,
                content = match.groupValues[2].removeSuffix("\n"),
                language = match.groupValues[1]
            )
        )
        rest = rest.substring(match.range.last + 1)
    }
    // 文本段再按块级公式切分
    val result = mutableListOf<Segment>()
    raw.forEach { segment ->
        if (segment.type != SegmentType.TEXT) {
            result.add(segment)
            return@forEach
        }
        var remain = segment.content
        while (true) {
            val match = displayLatexRegex.find(remain)
            if (match == null) {
                if (remain.isNotEmpty()) result.add(Segment(SegmentType.TEXT, remain))
                break
            }
            if (match.range.first > 0) {
                result.add(Segment(SegmentType.TEXT, remain.substring(0, match.range.first)))
            }
            result.add(Segment(SegmentType.LATEX, match.groupValues[1].trim()))
            remain = remain.substring(match.range.last + 1)
        }
    }
    return result
}

@Composable
fun AiMessageContent(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val segments = remember(text) { parseSegments(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEach { segment ->
            when (segment.type) {
                SegmentType.CODE -> CodeBlock(
                    code = segment.content,
                    language = segment.language,
                    modifier = Modifier.fillMaxWidth()
                )
                SegmentType.LATEX -> {
                    val state = rememberLatexImage(
                        latex = segment.content,
                        textSizeSp = 16f,
                        color = textColor
                    )
                    LatexImage(state = state)
                }
                SegmentType.TEXT -> MarkdownText(text = segment.content, textColor = textColor)
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String, language: String, modifier: Modifier = Modifier) {
    val surfaceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    Column(
        modifier = modifier
            .background(surfaceColor, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (language.isNotBlank()) {
            Text(
                text = language,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun MarkdownText(text: String, textColor: Color) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val lines = remember(text) { text.lines() }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { line ->
            when {
                line.startsWith("### ") -> Text(
                    text = inlineStyle(line.removePrefix("### "), textColor, primaryColor),
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor
                )
                line.startsWith("## ") -> Text(
                    text = inlineStyle(line.removePrefix("## "), textColor, primaryColor),
                    style = MaterialTheme.typography.titleLarge,
                    color = textColor
                )
                line.startsWith("# ") -> Text(
                    text = inlineStyle(line.removePrefix("# "), textColor, primaryColor),
                    style = MaterialTheme.typography.headlineSmall,
                    color = textColor
                )
                line.startsWith("- ") || line.startsWith("* ") -> Row {
                    Text(
                        text = "• ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                    Text(
                        text = inlineStyle(line.substring(2), textColor, primaryColor),
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                }
                orderedListRegex.containsMatchIn(line) -> {
                    val match = orderedListRegex.find(line)!!
                    Row {
                        Text(
                            text = match.groupValues[1] + " ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                        Text(
                            text = inlineStyle(match.groupValues[2], textColor, primaryColor),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }
                }
                line.startsWith("> ") -> Text(
                    text = inlineStyle(line.removePrefix("> "), textColor, primaryColor),
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = textColor.copy(alpha = 0.85f),
                    modifier = Modifier.padding(start = 8.dp)
                )
                line.isBlank() -> Text(text = " ", style = MaterialTheme.typography.bodyMedium)
                else -> Text(
                    text = inlineStyle(line, textColor, primaryColor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }
        }
    }
}

/** 行内格式：**粗体**、`行内代码`、$行内公式$（行内公式以主题色斜体展示）。 */
private fun inlineStyle(text: String, textColor: Color, primaryColor: Color): AnnotatedString =
    buildAnnotatedString {
        val inlineRegex = Regex("\\*\\*(.+?)\\*\\*|`(.+?)`|\\$([^$]+?)\\$")
        var rest = text
        while (true) {
            val match = inlineRegex.find(rest)
            if (match == null) {
                append(rest)
                break
            }
            append(rest.substring(0, match.range.first))
            when {
                match.groupValues[1].isNotEmpty() -> withStyle(
                    SpanStyle(fontWeight = FontWeight.Bold)
                ) { append(match.groupValues[1]) }
                match.groupValues[2].isNotEmpty() -> withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = textColor.copy(alpha = 0.08f))
                ) { append(match.groupValues[2]) }
                else -> withStyle(
                    SpanStyle(fontStyle = FontStyle.Italic, color = primaryColor)
                ) { append("$${match.groupValues[3]}$") }
            }
            rest = rest.substring(match.range.last + 1)
        }
    }
