package com.example.zhilu.export

import com.example.zhilu.domain.markup.InlineBrush
import com.example.zhilu.domain.model.EmphasisTone

/**
 * 导出用的**行内 token 流**。
 *
 * 为什么要多这一层：`inlineMarkupSegments` 只切出"普通段 / 被标记段"，而一段正文里
 * 真正要分流的东西还有三种 —— 行内公式、行内代码、行内链接。HTML 与 Markdown 两个导出器
 * 原先各写各的，结果就是"公式渲染成图片"这条 README 里写明的契约只在块级公式上成立，
 * **行内 `$…$` 在 HTML 里被原样当文字吐出去**（真机反馈：导出的 HTML 里公式没渲染）。
 *
 * 所以解析只做一次，两个导出器都消费同一个序列 —— 与"编辑态/只读态必须解析出同一组公式"
 * 是同一条道理：**两份解析就是两个真相来源**。
 */
internal sealed interface InlineExportToken {
    /** 普通文字，交给各导出格式自己转义。 */
    data class Plain(val text: String) : InlineExportToken

    /** 带语义色/笔触的文字段（`{{k:…}}`、`**…**`）。 */
    data class Styled(
        val text: String,
        val tone: EmphasisTone,
        val brush: InlineBrush
    ) : InlineExportToken

    /**
     * 公式。
     *
     * @param latex 剥掉定界符的源码，直接喂渲染器
     * @param display `$$…$$` 为 true（独占一行居中），`$…$` 为 false
     * 可选着色：整条公式被标记包住时才有（`{{k:$W \le 2^{n-1}$}}`）
     */
    data class Formula(
        val latex: String,
        val display: Boolean,
        val tone: EmphasisTone? = null,
        val brush: InlineBrush? = null
    ) : InlineExportToken

    /** `` `行内代码` `` */
    data class Code(val text: String) : InlineExportToken

    /** `[文字](url)` */
    data class Link(val text: String, val url: String) : InlineExportToken
}

/**
 * 行内 token 词法器。
 *
 * 分组：1 行内代码 / 2 块级公式 / 3 行内公式 / 4 链接文字 / 5 链接地址。
 * **`$$…$$` 必须排在 `$…$` 前面** —— 反过来的话行内规则会从第一个 `$` 一直吃到最后一个 `$`，
 * 把中间的文字全吞进公式源码里（同一个坑在只读态词法器里踩过一次，见 `InlineLatex` 的注释）。
 */
private val inlineExportTokenRegex =
    Regex("`(.+?)`|\\$\\$([^$]+?)\\$\\$|\\$([^$\\n]+?)\\$|\\[([^\\]\\n]+)]\\(([^)\\n]+)\\)")

/**
 * 把 `Block.content` 拆成导出用的 token 序列。
 *
 * 顺序固定：先按语义标记分段（[inlineMarkupSegments]），**再**在每一段内部识别
 * 公式 / 代码 / 链接。这样 `{{k:$W \le 2^{n-1}$}}` 里的公式既拿到了源码，
 * 又保留住了"整条被标记包住"这个信息（导出时用来给公式图上色）。
 */
internal fun inlineExportTokens(content: String): List<InlineExportToken> {
    val tokens = mutableListOf<InlineExportToken>()
    inlineMarkupSegments(content).forEach { segment ->
        val tone = (segment as? InlineMarkupSegment.Marked)?.tone
        val brush = (segment as? InlineMarkupSegment.Marked)?.brush
        tokens += tokenizeSegment(segment.text, tone, brush)
    }
    return tokens
}

private fun tokenizeSegment(
    text: String,
    tone: EmphasisTone?,
    brush: InlineBrush?
): List<InlineExportToken> {
    if (text.isEmpty()) return emptyList()
    val tokens = mutableListOf<InlineExportToken>()
    var cursor = 0

    /**
     * 段内的普通文字要**继承本段的语义角色**。
     *
     * 不能直接吐 [InlineExportToken.Plain] —— `{{i:正}}` 这种"标记里没有公式"的段
     * 会因此丢掉全部强调信息（第一版就是这么写的，导出 HTML 里再也看不到语义色）。
     */
    fun flushPlain(until: Int) {
        if (until <= cursor) return
        val chunk = text.substring(cursor, until)
        tokens += if (tone != null && brush != null) {
            InlineExportToken.Styled(chunk, tone, brush)
        } else {
            InlineExportToken.Plain(chunk)
        }
    }

    for (match in inlineExportTokenRegex.findAll(text)) {
        // 具名取值而不是解构声明：解构会把整个匹配值也带进来（`component1`），
        // 多出一个用不上的变量；而且分组一改就静默错位（只读态词法器踩过这个坑）。
        val code = match.groupValues[1]
        val blockMath = match.groupValues[2]
        val inlineMath = match.groupValues[3]
        val linkText = match.groupValues[4]
        val linkUrl = match.groupValues[5]
        flushPlain(match.range.first)
        when {
            code.isNotEmpty() -> tokens += InlineExportToken.Code(code)
            blockMath.isNotEmpty() ->
                tokens += InlineExportToken.Formula(blockMath.trim(), display = true, tone = tone, brush = brush)
            inlineMath.isNotEmpty() ->
                tokens += InlineExportToken.Formula(inlineMath.trim(), display = false, tone = tone, brush = brush)
            linkText.isNotEmpty() -> tokens += InlineExportToken.Link(linkText, linkUrl)
            else -> tokens += InlineExportToken.Plain(match.value)
        }
        cursor = match.range.last + 1
    }
    flushPlain(text.length)
    return tokens
}
