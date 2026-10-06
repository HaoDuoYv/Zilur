package com.example.zhilu.ui.component

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
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
import com.example.zhilu.ui.theme.LocalThemePalette

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
    // 语义标记的色值跟着外观走。**在这里读一次**，而不是让 8 个调用点各传一遍 ——
    // RichText 是只读路径唯一的入口，读 CompositionLocal 是最省事也最难漏的做法。
    val palette = LocalThemePalette.current
    // 行内链接用主题强调色；buildInlineLatexText 是普通函数，读不了 MaterialTheme，所以在这里取好传进去
    val linkColor = MaterialTheme.colorScheme.primary
    val parts = remember(text, color, isDark, highlight, accessibleEmphasis, linkColor, palette) {
        buildInlineLatexText(text, color, isDark, linkColor, highlight, accessibleEmphasis, palette)
    }
    val inlineContent = rememberInlineLatexContent(
        formulas = parts.formulas,
        textSizeSp = if (style.fontSize.isSpecified) style.fontSize.value else 14f,
        color = color,
        // 与 formulas 一一对应：公式被语义标记覆盖时，墨色与底色由公式图自己画
        // （Compose 不会把 span 样式套到 inline content 上）
        spans = parts.spans,
        darkTheme = isDark,
        accessibleEmphasis = accessibleEmphasis,
        palette = palette
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
