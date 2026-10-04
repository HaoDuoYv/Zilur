package com.example.zhilu.ui.note.latex

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.zhilu.domain.markup.InlineMarkup
import com.example.zhilu.domain.markup.InlineSpan
import com.example.zhilu.ui.component.FindHighlight
import com.example.zhilu.ui.theme.inlineToneSpanStyle

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

/**
 * 解析结果：带占位标记的富文本 + 占位顺序对应的公式源码。
 *
 * [spans] 与 [formulas] 一一对应：覆盖该公式的语义标记（没有则 null）。
 * **必须有这一份** —— Compose 不会把 span 的背景/字色套到 inline content 上，
 * 公式图的着色只能由内容自身去画（见 [inlineLatexContent]）。早期没有它，
 * 结果是"编辑态里带标记的公式有色、只读态只剩左边一条细缝"（真机反馈）。
 */
class InlineLatexParts(
    val text: AnnotatedString,
    val formulas: List<String>,
    val spans: List<InlineSpan?> = emptyList()
)

/**
 * 行内 token 词法器：`` `行内代码` ``、`$行内公式$` / `$$块级公式$$`、`[文字](url)` 行内链接。
 *
 * **块级 `$$…$$` 必须排在 `$…$` 前面**。反过来的话 `$([^$]+?)$` 会先吃掉**第一个 `$` 与最后一个 `$`
 * 之间的全部内容** —— `块级：\n$$E=mc^2$$\n行内：$a$` 会被解析成一条源码为
 * `E=mc^2$$\n行内：` 的"公式"，中间那段文字**在只读态直接消失**（编辑态不会，因为那边走
 * `MathSpans`、块级优先）。同一个块级公式，编辑态渲染、只读态吞字，是很容易被当成
 * "公式丢了"的那种不一致。
 *
 * **加粗不在其中** —— `**…**` 已由 `InlineMarkup.parseSpans` 拆成 `InlineBrush.BOLD` 的 span
 * （设计文档 §10 P3），走到这里时可见文本里已经没有 `**` 了。留着那条分支只会制造
 * "两处都在管加粗"的第二真相来源。
 *
 * 分组：1 行内代码 / 2 块级公式源码 / 3 行内公式源码 / 4 链接文字 / 5 链接地址。
 */
private val inlineTokenRegex =
    Regex("`(.+?)`|\\$\\$([^$]+?)\\$\\$|\\$([^$\\n]+?)\\$|\\[([^\\]\\n]+)]\\(([^)\\n]+)\\)")

/**
 * 解析行内格式：`**粗体**`、`` `行内代码` ``、`$行内公式$`，**并套用语义标记的着色**。
 *
 * 优先级固定为：行内代码 > 行内公式 > 加粗 > 语义标记（`{{k:…}}`）。
 * 语义标记由 `InlineMarkup.parseSpans` 先拆出来 → 得到"可见文本 + 标记区间"，
 * 再在每段区间内跑既有词法器，所以两种语法不会互相吃掉。
 *
 * @param darkTheme 决定标记用浅色还是深色那套语义色；调用方从主题取。
 */
fun buildInlineLatexText(
    raw: String,
    textColor: Color,
    darkTheme: Boolean = false,
    /** 行内链接的强调色。**由调用方传**：本函数是普通函数（非 @Composable），读不了 MaterialTheme。 */
    linkColor: Color = Color.Unspecified,
    /** 页内查找的命中高亮（§6.4）。为 null 时完全不影响既有渲染。 */
    highlight: FindHighlight? = null,
    /** 无障碍色板（§3.9）。 */
    accessibleEmphasis: Boolean = false
): InlineLatexParts {
    val parsed = InlineMarkup.parseSpans(raw)
    val visible = parsed.visibleText
    val toneSpans = parsed.spans
    val formulas = mutableListOf<String>()
    val formulaSpans = mutableListOf<InlineSpan?>()

    // 命中区间一律在**可见文本**坐标系上算：标记字符已经被 parseSpans 剥掉，
    // 若拿原文下标去高亮，只要块里有一个 `{{…}}`，后面所有高亮都会整体偏移。
    val matchRanges = if (highlight == null) {
        emptyList()
    } else {
        findMatchRanges(visible, highlight.query)
    }
    val highlightStyle = highlight?.let {
        SpanStyle(
            background = it.background,
            color = it.textColor,
            fontWeight = FontWeight.Bold
        )
    }
    // 在 composable 上下文里先取出来：下面 buildAnnotatedString 的 lambda 不是 composable，
    // 在那里读 MaterialTheme 会编译不过（踩过）
    val linkInk = linkColor

    val annotated = buildAnnotatedString {
        /** 追加普通文字；有命中时按命中边界切开并套高亮。 */
        fun emitPlain(from: Int, to: Int) {
            if (from >= to) return
            if (highlightStyle == null || matchRanges.isEmpty()) {
                append(visible.substring(from, to))
                return
            }
            var cursor = from
            while (cursor < to) {
                val hit = matchRanges.firstOrNull { cursor >= it.first && cursor < it.second }
                val nextStart = matchRanges.firstOrNull { it.first > cursor }?.first ?: to
                val end = if (hit != null) minOf(hit.second, to) else minOf(nextStart, to)
                if (end <= cursor) {
                    append(visible.substring(cursor, to))
                    return
                }
                if (hit != null) {
                    withStyle(highlightStyle) { append(visible.substring(cursor, end)) }
                } else {
                    append(visible.substring(cursor, end))
                }
                cursor = end
            }
        }

        // 把可见文本按"是否落在标记区间内"切成若干段，段内再解析既有 tokens。
        fun emitTokens(from: Int, to: Int) {
            var cursor = from
            while (cursor < to) {
                val match = inlineTokenRegex.find(visible, cursor)
                if (match == null || match.range.first >= to) {
                    emitPlain(cursor, to)
                    return
                }
                if (match.range.first > cursor) {
                    emitPlain(cursor, match.range.first)
                }
                val codeBody = match.groupValues[1]
                val blockMathBody = match.groupValues[2]
                val inlineMathBody = match.groupValues[3]
                val linkText = match.groupValues[4]
                // 公式源码：行内 `$…$` 与块级 `$$…$$` 在正文里渲染成同一种东西（都是公式图），
                // 差别只在源码形态与是否允许跨行；这里只需各自去掉定界符。
                val latexSource = inlineMathBody.ifEmpty { blockMathBody }.trim()
                // 注意分支顺序：`latexSource` 与 `linkText` 分别来自 group3 / group4，
                // **互斥**（正则的分支只会命中一个），顺序本身不影响判定；
                // 但两者都要在 `codeBody` 之后判，且不要写成"先判某个 group 非空"的旧口径 ——
                // 分组号一变，那种写法会把公式当成链接文字吞掉（本轮踩过）。
                when {
                    codeBody.isNotEmpty() -> withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = textColor.copy(alpha = 0.08f)
                        )
                    ) { append(codeBody) }

                    latexSource.isNotEmpty() -> {
                        // 退化文案保留原始写法：万一图片渲染不出来，用户看到的仍是「可读」的公式源码。
                        appendInlineContent(inlineLatexId(formulas.size), alternateText = "\$$latexSource\$")
                        formulas += latexSource
                        // 记下覆盖这条公式的标记：inline content 拿不到 span 样式，
                        // 着色只能由内容自己画（见 InlineLatexParts.spans 的说明）
                        formulaSpans += toneSpans.firstOrNull {
                            it.start <= match.range.first && it.end >= match.range.last + 1
                        }
                    }

                    // 行内链接：只显示文字（不显示 URL），强调色 + 下划线
                    linkText.isNotEmpty() -> withStyle(
                        SpanStyle(
                            color = linkInk,
                            textDecoration = TextDecoration.Underline
                        )
                    ) { append(linkText) }
                }
                cursor = match.range.last + 1
            }
        }

        var index = 0
        while (index < visible.length) {
            val span = toneSpans.firstOrNull { it.contains(index) }
            val segmentEnd = span?.end?.coerceIn(0, visible.length)
                ?: (toneSpans.firstOrNull { it.start > index }?.start ?: visible.length)
                .coerceIn(index, visible.length)
            val end = segmentEnd.coerceAtLeast(index + 1).coerceAtMost(visible.length)
            if (span != null) {
                pushStyle(
                    inlineToneSpanStyle(span.tone, span.brush, darkTheme, accessibleEmphasis)
                )
                emitTokens(index, end)
                pop()
            } else {
                emitTokens(index, end)
            }
            index = end
        }
    }
    return InlineLatexParts(annotated, formulas, formulaSpans)
}

/** 在 [text] 里找出 [query] 的全部命中区间（大小写不敏感、允许重叠推进）。 */
private fun findMatchRanges(text: String, query: String): List<Pair<Int, Int>> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty() || text.isEmpty()) return emptyList()
    val haystack = text.lowercase()
    val ranges = mutableListOf<Pair<Int, Int>>()
    var from = 0
    while (true) {
        val at = haystack.indexOf(needle, from)
        if (at < 0) break
        ranges += at to (at + needle.length)
        from = at + needle.length
    }
    return ranges
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
    color: Color,
    /** 与 [formulas] 一一对应：覆盖该公式的语义标记，决定公式图的墨色与底色。 */
    spans: List<InlineSpan?> = emptyList(),
    darkTheme: Boolean = false,
    accessibleEmphasis: Boolean = false
): Map<String, InlineTextContent> {
    if (formulas.isEmpty()) return emptyMap()
    val density = LocalDensity.current
    return formulas.mapIndexed { index, latex ->
        val content = inlineLatexContent(
            latex = latex,
            textSizeSp = textSizeSp,
            color = color,
            density = density,
            span = spans.getOrNull(index),
            darkTheme = darkTheme,
            accessibleEmphasis = accessibleEmphasis
        )
        inlineLatexId(index) to content
    }.toMap()
}

@Composable
private fun inlineLatexContent(
    latex: String,
    textSizeSp: Float,
    color: Color,
    density: androidx.compose.ui.unit.Density,
    span: InlineSpan? = null,
    darkTheme: Boolean = false,
    accessibleEmphasis: Boolean = false
): InlineTextContent {
    // 被语义标记覆盖时，公式用**角色的墨色**绘制，并在图后面铺上同一支笔触的底色 ——
    // Compose 不会把 span 的背景/字色套到 inline content 上，所以这两件事只能自己画。
    // 样式直接取只读路径那支 [inlineToneSpanStyle]，保证"文字上的标记"与"公式上的标记"同色。
    val toneStyle = span?.let { inlineToneSpanStyle(it.tone, it.brush, darkTheme, accessibleEmphasis) }
    val inkColor = toneStyle?.color?.takeIf { it != Color.Unspecified } ?: color
    val backgroundColor = toneStyle?.background ?: Color.Unspecified

    // 同一公式的渲染结果全局缓存，滚动回看或重组时命中缓存直接出图，不再闪烁。
    val state = rememberLatexImage(latex = latex, textSizeSp = textSizeSp, color = inkColor)
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
        // 底色铺在公式图**后面**：Compose 不会替 inline content 画 span 背景，
        // 所以要在这里自己兜住 —— 否则带标记的公式在只读态只剩左边一条细缝（真机反馈）。
        val backgroundModifier = if (backgroundColor != Color.Unspecified) {
            Modifier.background(backgroundColor)
        } else {
            Modifier
        }
        if (current != null) {
            val w = with(density) { current.width.toDp() }
            val h = with(density) { current.height.toDp() }
            Image(
                bitmap = current,
                contentDescription = latex,
                modifier = backgroundModifier
                    .width(w)
                    .height(h)
            )
        } else {
            // 尚未渲染完成或渲染失败：退化展示源码，保证信息不丢失。
            Text(
                text = latex,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = inkColor.copy(alpha = 0.85f),
                modifier = backgroundModifier
            )
        }
    }
}
