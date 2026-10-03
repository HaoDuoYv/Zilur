package com.example.zhilu.ui.note.blocks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.note.latex.LatexImage
import com.example.zhilu.ui.note.latex.rememberLatexImage
import com.example.zhilu.ui.theme.ZhiLuType

@Composable
fun LatexBlockEditor(
    value: String,
    onValueChange: (String) -> Unit,
    /** 本块 id：用于「转为行内公式」（见 [LocalFormulaConversions]）。 */
    blockId: Long = -1L,
    modifier: Modifier = Modifier
) {
    val demoteFormula = LocalFormulaConversions.current?.demote
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            text = "输入 LaTeX 公式…",
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            )
            if (value.isNotBlank()) {
                LatexPreview(value = value)
                // 反向出口：公式太长可以留在块里，但"这句里就缺个符号"时应该能塞回句子。
                // 就地转成文本块（内容 `$源码$`），转完就是一行普通文字，随用户剪切。
                if (demoteFormula != null) {
                    Text(
                        text = "转为行内公式",
                        style = ZhiLuType.meta,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.End)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { demoteFormula(blockId) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScrollableLatexBox(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(bottom = 8.dp)
    ) {
        content()
    }
}

@Composable
private fun LatexPreview(
    value: String,
    modifier: Modifier = Modifier
) {
    if (value.isBlank()) return

    val state = rememberLatexImage(
        latex = value,
        textSizeSp = 18f,
        color = MaterialTheme.colorScheme.onSurface
    )
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScrollableLatexBox(scrollState = scrollState) {
                LatexImage(
                    state = state,
                    modifier = Modifier.wrapContentWidth()
                )
            }
        }
    }
}

@Composable
fun ReadOnlyLatexBlockContent(
    value: String,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        if (value.isBlank()) {
            Text(
                text = "空公式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScrollableLatexBox(scrollState = scrollState) {
                    val state = rememberLatexImage(
                        latex = value,
                        textSizeSp = 20f,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LatexImage(
                        state = state,
                        modifier = Modifier.wrapContentWidth()
                    )
                }
            }
        }
    }
}
