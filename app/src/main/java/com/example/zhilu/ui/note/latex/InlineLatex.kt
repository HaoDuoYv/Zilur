package com.example.zhilu.ui.note.latex

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 行内富文本渲染支持。
 *
 * Compose 的文本无法在 AnnotatedString 里直接放图片，但可以通过 [InlineTextContent]
 * 在文本流中嵌入可组合项。做法：
 * 1. [buildInlineLatexText] 解析行内格式，把 `$公式$` 换成一段用
 *    [appendInlineContent] 标记的占位片段，并把公式源码按顺序收集起来；
 * 2. [rememberInlineLatexContent] 为每个占位 id 准备一个 [InlineTextContent]，
 *    内部异步渲染 LaTeX 图片，渲染完成前先退化展示源码，避免出现空洞。
 *
 * 注意：占位必须用 [appendInlineContent] 写入——它会给片段打上
 * `androidx.compose.foundation.text.inlineContent` 字符串注解，
 * [androidx.compose.foundation.text.BasicText] 正是靠这个注解把片段替换成可组合项。
 * 直接向文本里塞一个私有区字符是不行的，那样只会渲染成缺字方框。
 */

private const val INLINE_ID_PREFIX = "zhilu-latex-"

/** 第 [index] 个行内公式的占位 id，与 [rememberInlineLatexContent] 的 key 一一对应。 */
fun inlineLatexId(index: Int): String = "$INLINE_ID_PREFIX$index"

/** 解析结果：带占位标记的富文本 + 占位顺序对应的公式源码。 */
class InlineLatexParts(
    val text: AnnotatedString,
    val formulas: List<String>
)

private val inlineTokenRegex = Regex("\\*\\*(.+?)\\*\\*|`(.+?)`|\\$([^$]+?)\\$")

/**
 * 解析行内格式：`**粗体**`、`` `行内代码` ``、`$行内公式$`。
 * 公式替换为 inlineContent 占位片段，交由 [rememberInlineLatexContent] 渲染成图片，
 * 不再原样显示 `$...$`。
 */
fun buildInlineLatexText(raw: String, textColor: Color): InlineLatexParts {
    val formulas = mutableListOf<String>()
    val annotated = buildAnnotatedString {
        var rest = raw
        while (true) {
            val match = inlineTokenRegex.find(rest)
            if (match == null) {
                append(rest)
                break
            }
            append(rest.substring(0, match.range.first))
            when {
                match.groupValues[1].isNotEmpty() ->
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }

                match.groupValues[2].isNotEmpty() ->
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = textColor.copy(alpha = 0.08f)
                        )
                    ) { append(match.groupValues[2]) }

                else -> {
                    val latex = match.groupValues[3].trim()
                    // 退化文案保留原始写法：万一图片渲染不出来，用户看到的仍是「可读」的公式源码。
                    appendInlineContent(inlineLatexId(formulas.size), alternateText = "\$$latex\$")
                    formulas += latex
                }
            }
            rest = rest.substring(match.range.last + 1)
        }
    }
    return InlineLatexParts(annotated, formulas)
}

/**
 * 为 [formulas] 生成 [InlineTextContent] 映射，key 与文本中的占位 id 一一对应。
 *
 * @param textSizeSp 行内公式的目标字号，通常与所在正文一致
 */
@Composable
fun rememberInlineLatexContent(
    formulas: List<String>,
    textSizeSp: Float,
    color: Color
): Map<String, InlineTextContent> {
    if (formulas.isEmpty()) return emptyMap()
    val density = LocalDensity.current
    return formulas.mapIndexed { index, latex ->
        val content = inlineLatexContent(latex, textSizeSp, color, density)
        inlineLatexId(index) to content
    }.toMap()
}

@Composable
private fun inlineLatexContent(
    latex: String,
    textSizeSp: Float,
    color: Color,
    density: androidx.compose.ui.unit.Density
): InlineTextContent {
    // 同一公式的渲染结果全局缓存，滚动回看或重组时命中缓存直接出图，不再闪烁。
    val state = rememberLatexImage(latex = latex, textSizeSp = textSizeSp, color = color)
    val image = (state as? LatexRenderState.Success)?.image

    val widthEm = remember(image, latex) {
        image?.let { with(density) { it.width.toSp().value } }?.sp
            ?: (latex.length * 0.55f).coerceIn(1.2f, 12f).em
    }
    val heightSp = remember(image, textSizeSp) {
        image?.let { with(density) { it.height.toSp() } } ?: (textSizeSp * 1.15f).sp
    }

    return InlineTextContent(
        placeholder = Placeholder(
            width = widthEm,
            height = heightSp,
            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
        )
    ) {
        val current = image
        if (current != null) {
            val w = with(density) { current.width.toDp() }
            val h = with(density) { current.height.toDp() }
            Image(
                bitmap = current,
                contentDescription = latex,
                modifier = Modifier
                    .width(w)
                    .height(h)
            )
        } else {
            // 尚未渲染完成或渲染失败：退化展示源码，保证信息不丢失。
            Text(
                text = latex,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = color.copy(alpha = 0.85f)
            )
        }
    }
}
