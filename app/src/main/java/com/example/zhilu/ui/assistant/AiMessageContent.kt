package com.example.zhilu.ui.assistant

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.markup.MarkdownTable
import com.example.zhilu.ui.component.DividerTokens
import com.example.zhilu.ui.component.MarkdownTableView
import com.example.zhilu.ui.component.RichText
import com.example.zhilu.ui.component.ZhiLuDivider
import com.example.zhilu.ui.note.latex.LatexImage
import com.example.zhilu.ui.note.latex.rememberLatexImage

/**
 * AI 消息富文本渲染：支持代码块（``` 围栏）、块级公式（$$...$$）、
 * 标题（#/##/###）、无序/有序列表、引用（>）、表格（| a | b | + 分隔行）、
 * 行内 **粗体**、`行内代码`、$行内公式$。
 * 流式输出期间未闭合的围栏/公式按普通文本显示，不会吞字。
 */

private enum class SegmentType { TEXT, CODE, LATEX }

private data class Segment(val type: SegmentType, val content: String, val language: String = "")

private val codeFenceRegex = Regex("```([\\w+#-]*)[ \\t]*\\n?([\\s\\S]*?)```")
private val displayLatexRegex = Regex("\\$\\$([\\s\\S]+?)\\$\\$")
private val orderedListRegex = Regex("^(\\d+\\.) (.*)")

/** Markdown 行被归约为「表格块」或「普通行」两类。 */
private sealed interface MdBlock {
    data class Line(val text: String) : MdBlock
    data class Table(val value: MarkdownTable.Table) : MdBlock
}

/**
 * 把 Markdown 行序列归约为块序列：连续的 `| a | b |` + 分隔行 + 数据行识别为一张表，
 * 其余按普通行保留（逐行渲染逻辑不变）。
 *
 * 表格的判据与切分全在 [MarkdownTable] 里 —— 笔记的表格块走的是同一份解析，
 * 两处各写一套迟早会分叉（一边认得多、一边认得少）。
 */
private fun parseMarkdownBlocks(lines: List<String>): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val table = MarkdownTable.parseAt(lines, i)
        if (table != null) {
            blocks.add(MdBlock.Table(table))
            i += table.lineCount
        } else {
            blocks.add(MdBlock.Line(lines[i]))
            i++
        }
    }
    return blocks
}

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
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val surfaceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    Column(
        modifier = modifier
            .background(surfaceColor, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifBlank { "代码" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "复制",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable {
                        clipboard.setText(AnnotatedString(code))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
        ZhiLuDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(10.dp)
        )
    }
}

@Composable
private fun MarkdownText(text: String, textColor: Color) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val blocks = remember(text) { parseMarkdownBlocks(text.lines()) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Table -> MarkdownTableView(
                    header = block.value.header,
                    rows = block.value.rows,
                    textColor = textColor,
                    accentColor = primaryColor
                )
                is MdBlock.Line -> MarkdownLine(block.text, textColor, primaryColor)
            }
        }
    }
}

@Composable
private fun MarkdownLine(line: String, textColor: Color, primaryColor: Color) {
    when {
        line.startsWith("### ") -> RichText(
            text = line.removePrefix("### "),
            style = MaterialTheme.typography.titleMedium,
            color = textColor
        )
        line.startsWith("## ") -> RichText(
            text = line.removePrefix("## "),
            style = MaterialTheme.typography.titleLarge,
            color = textColor
        )
        line.startsWith("# ") -> RichText(
            text = line.removePrefix("# "),
            style = MaterialTheme.typography.headlineSmall,
            color = textColor
        )
        line.startsWith("- ") || line.startsWith("* ") -> Row {
            Text(
                text = "• ",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
            RichText(
                text = line.substring(2),
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
                RichText(
                    text = match.groupValues[2],
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }
        }
        line.startsWith("> ") -> Row(
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .background(primaryColor.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            )
            RichText(
                text = line.removePrefix("> "),
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = textColor.copy(alpha = 0.85f),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        line.isBlank() -> Text(text = " ", style = MaterialTheme.typography.bodyMedium)
        else -> RichText(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor
        )
    }
}

/**
 * Markdown 表格的渲染已抽到 `ui/component/MarkdownTableView.kt` —— 笔记的表格块
 * 用的是同一份组件，两处观感天然一致。
 */
