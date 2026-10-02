package com.example.zhilu.ui.component

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified
import com.example.zhilu.ui.note.latex.buildInlineLatexText
import com.example.zhilu.ui.note.latex.rememberInlineLatexContent
import com.example.zhilu.ui.theme.LocalAccessibleEmphasis
import com.example.zhilu.ui.theme.LocalExtendedColors

/**
 * 支持行内公式的文本组件：在普通文本里把 `$...$` 真正渲染成 LaTeX 图片，
 * 同时保留 `**粗体**` 与 `` `行内代码` `` 格式。
 *
 * 之所以不用 Material3 的 [androidx.compose.material3.Text]：行内嵌入可组合项
 * 需要 [BasicText] 的 `inlineContent` 能力。
 */
@Composable
fun RichText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = style.color.takeIf { it != Color.Unspecified }
        ?: androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null
) {
    val isDark = LocalExtendedColors.current.isDark
    val highlight = LocalFindHighlight.current
    val accessibleEmphasis = LocalAccessibleEmphasis.current
    val parts = remember(text, color, isDark, highlight, accessibleEmphasis) {
        buildInlineLatexText(text, color, isDark, highlight, accessibleEmphasis)
    }
    val inlineContent = rememberInlineLatexContent(
        formulas = parts.formulas,
        textSizeSp = if (style.fontSize.isSpecified) style.fontSize.value else 14f,
        color = color
    )
    BasicText(
        text = parts.text,
        modifier = modifier,
        style = style.copy(
            color = color,
            fontWeight = fontWeight ?: style.fontWeight,
            textAlign = textAlign ?: style.textAlign
        ),
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = true,
        maxLines = maxLines,
        minLines = minLines,
        inlineContent = inlineContent
    )
}
