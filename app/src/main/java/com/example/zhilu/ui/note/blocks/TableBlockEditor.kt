package com.example.zhilu.ui.note.blocks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.markup.MarkdownTable
import com.example.zhilu.ui.component.MarkdownTableView
import com.example.zhilu.ui.component.QuietAction
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 表格块的编辑态：**等宽文本编辑 + 实时预览**。
 *
 * 没有做结构化网格编辑器（单元格聚焦、增删行列、IME 换格）。理由：
 * 那在项目里是全新基建，手势与状态机风险高；而表格块的内容本来就是一段可读的
 * Markdown —— 与 [CodeBlockEditor] / [LatexBlockEditor] 保持同一种"等宽文本 + 渲染"
 * 形态（这三者都是代码类内容，不套 `AppTextField`），用户写管道语法、下面立刻看到网格。
 *
 * 空内容时给一个模板按钮：管道语法并不直观，给个骨架比让人对着空框发呆强。
 */
@Composable
fun TableBlockEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val table = MarkdownTable.parseWhole(value)
    val textStyle = MaterialTheme.typography.bodyLarge.merge(
        TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace
        )
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Sm + Spacing.Xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp),
                textStyle = textStyle,
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = "| 列 1 | 列 2 |\n| --- | --- |",
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            )

            if (table != null) {
                MarkdownTableView(header = table.header, rows = table.rows)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
                    Text(
                        text = "还差一行表头分隔（第二行写 | --- | --- |）",
                        style = ZhiLuType.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (value.isBlank()) {
                        QuietAction(
                            label = "插入表格模板",
                            onClick = { onValueChange(MarkdownTable.TEMPLATE) }
                        )
                    }
                }
            }
        }
    }
}
