package com.example.zhilu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Markdown 表格的渲染：表头主色淡底 + 等宽单元格 + 行间细分隔线，超宽时可横向滚动。
 * 单元格统一宽度，保证各列对齐；窄屏下不挤压、不换行错位。
 *
 * **AI 消息与笔记表格块共用这一份**。原先它只活在 `AiMessageContent` 里（private），
 * 于是「AI 聊天能显示表格、笔记里不能」——抽出来是为了让两边观感天然一致，
 * 也避免第二份表格渲染迟早跟第一份分叉。
 *
 * 样式全部走语义 token（`outlineVariant` / 传入的强调色 / `onSurface` 低透明度斑马纹），
 * 自动跟随「纸墨 / 动森」两套外观。
 *
 * @param accentColor 表头淡底与表头字色。调用方按场景给：AI 消息传消息气泡的强调色，
 *   笔记块用主题 primary。
 */
@Composable
fun MarkdownTableView(
    header: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerBg = accentColor.copy(alpha = 0.12f)
    val stripeBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f)
    val shape = RoundedCornerShape(8.dp)
    val columns = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)

    Column(
        modifier = modifier
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .horizontalScroll(rememberScrollState())
    ) {
        Row(modifier = Modifier.background(headerBg)) {
            repeat(columns) { index ->
                MarkdownTableCell(
                    text = header.getOrElse(index) { "" },
                    textColor = textColor,
                    accentColor = accentColor,
                    isHeader = true
                )
            }
        }
        ZhiLuDivider(color = borderColor)
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = if (rowIndex % 2 == 1) Modifier.background(stripeBg) else Modifier
            ) {
                repeat(columns) { index ->
                    MarkdownTableCell(
                        text = row.getOrElse(index) { "" },
                        textColor = textColor,
                        accentColor = accentColor,
                        isHeader = false
                    )
                }
            }
            if (rowIndex < rows.lastIndex) {
                ZhiLuDivider(
                    color = borderColor.copy(alpha = 0.5f),
                    thickness = DividerTokens.Hairline
                )
            }
        }
    }
}

@Composable
private fun MarkdownTableCell(
    text: String,
    textColor: Color,
    accentColor: Color,
    isHeader: Boolean
) {
    RichText(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isHeader) accentColor else textColor,
        fontWeight = if (isHeader) FontWeight.SemiBold else null,
        modifier = Modifier
            .width(92.dp)
            .padding(horizontal = 8.dp, vertical = 7.dp)
    )
}
