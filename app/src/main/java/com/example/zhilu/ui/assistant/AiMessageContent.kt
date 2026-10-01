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
import androidx.compose.material3.HorizontalDivider
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
import com.example.zhilu.ui.component.RichText
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
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
}

private val tableSeparatorCellRegex = Regex("^:?-{2,}:?$")

private fun isTableRow(line: String): Boolean = line.count { it == '|' } >= 2

private fun isTableSeparator(line: String): Boolean {
    val cells = splitTableCells(line)
    return cells.isNotEmpty() && cells.all { tableSeparatorCellRegex.matches(it) }
}

/** 按未转义的 `|` 切分单元格，并还原模型输出的 `\|` 转义。 */
private fun splitTableCells(line: String): List<String> {
    val token = "\u0000"
    return line.trim()
        .removePrefix("|")
        .removeSuffix("|")
        .replace("\\|", token)
        .split('|')
        .map { it.trim().replace(token, "|") }
}

/**
 * 把 Markdown 行序列归约为块序列：连续的 `| a | b |` + 分隔行 + 数据行识别为一张表，
 * 其余按普通行保留（逐行渲染逻辑不变）。
 */
private fun parseMarkdownBlocks(lines: List<String>): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (isTableRow(line) && !isTableSeparator(line) &&
            i + 1 < lines.size && isTableSeparator(lines[i + 1])
        ) {
            val header = splitTableCells(line)
            val rows = mutableListOf<List<String>>()
            var j = i + 2
            while (j < lines.size && isTableRow(lines[j]) && !isTableSeparator(lines[j])) {
                rows.add(splitTableCells(lines[j]))
                j++
            }
            blocks.add(MdBlock.Table(header, rows))
            i = j
        } else {
            blocks.add(MdBlock.Line(line))
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
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
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
                is MdBlock.Table -> MarkdownTable(block, textColor, primaryColor)
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
 * Markdown 表格：表头主色淡底 + 等宽单元格 + 行间细分隔线，超宽时可横向滚动。
 * 单元格统一宽度，保证各列对齐；窄屏下不挤压、不换行错位。
 */
@Composable
private fun MarkdownTable(table: MdBlock.Table, textColor: Color, primaryColor: Color) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerBg = primaryColor.copy(alpha = 0.12f)
    val stripeBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f)
    val shape = RoundedCornerShape(8.dp)
    val columns = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0)

    Column(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .horizontalScroll(rememberScrollState())
    ) {
        Row(modifier = Modifier.background(headerBg)) {
            repeat(columns) { index ->
                TableCell(
                    text = table.header.getOrElse(index) { "" },
                    textColor = textColor,
                    primaryColor = primaryColor,
                    isHeader = true
                )
            }
        }
        HorizontalDivider(color = borderColor)
        table.rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = if (rowIndex % 2 == 1) Modifier.background(stripeBg) else Modifier
            ) {
                repeat(columns) { index ->
                    TableCell(
                        text = row.getOrElse(index) { "" },
                        textColor = textColor,
                        primaryColor = primaryColor,
                        isHeader = false
                    )
                }
            }
            if (rowIndex < table.rows.lastIndex) {
                HorizontalDivider(
                    color = borderColor.copy(alpha = 0.5f),
                    thickness = 0.5.dp
                )
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    textColor: Color,
    primaryColor: Color,
    isHeader: Boolean
) {
    RichText(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isHeader) primaryColor else textColor,
        fontWeight = if (isHeader) FontWeight.SemiBold else null,
        modifier = Modifier
            .width(92.dp)
            .padding(horizontal = 8.dp, vertical = 7.dp)
    )
}
