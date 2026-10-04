package com.example.zhilu.domain.markup

/** 行内原子的种类。 */
enum class InlineKind {
    /** `$…$` 行内公式：渲染成公式图，源码隐藏。 */
    MATH,

    /** `` `code` `` 行内代码：**就地套样式**（等宽 + 底色），源码不隐藏。 */
    CODE,

    /** `[文字](url)` 行内链接：**就地套样式**（强调色 + 下划线），显示文字、源码不隐藏。 */
    LINK
}

/**
 * 行内原子：**可见文本**上的一个区间 + 种类。
 *
 * 与 `InlineSpan` 同一套坐标（块内可见文本偏移，`{{}}` 语法字符已剥离，
 * 但 `$…$` 的源码仍占位）—— 这是刻意对齐的：原子因此自动获得与标记同等的编辑行为
 * （随文字增删伸缩、被删掉就消失），`InlineSpanAdjuster` 一行都不用改。
 */
data class InlineNode(
    val kind: InlineKind,
    val start: Int,
    val end: Int
) {
    val length: Int get() = (end - start).coerceAtLeast(0)

    fun contains(offset: Int): Boolean = offset >= start && offset <= end

    /**
     * 原子的"内容"：去掉定界符之后的源码。
     *
     * `$W \le 2^{n-1}$` → `W \le 2^{n-1}`；`$$E=mc^2$$` → `E=mc^2`。
     *
     * **两种定界符都要去掉**：块级 `$$…$$` 落到这里时，只剥一层会剩下 `$E=mc^2$`，
     * 于是同一个块级公式在编辑态交给渲染器的是带 `$` 的源码、在只读态是裸源码
     * —— 两条路径喂给 `sanitizeLatex` 的输入不同，渲染结果就可能不同。
     */
    fun contentOf(text: String): String {
        val raw = text.substring(start.coerceIn(0, text.length), end.coerceIn(0, text.length))
        return raw
            .removeSurrounding("$$")
            .removeSurrounding("$")
            .trim()
    }
}

/**
 * 从可见文本里解析出行内原子。
 *
 * 纯函数、不依赖 Compose，方便单测。
 */
object InlineNodes {

    /** `$…$` 行内公式（不跨行）；块级 `$$…$$` 由 [MathSpans] 优先吃掉，不会误判成两个行内。 */
    private val inlineMath = Regex("\\$([^$\\n]+)\\$")

    /** `` `code` `` 行内代码：单行、非空。与 `InlineLatex` 的词法器保持同一口径。 */
    private val inlineCode = Regex("`([^`\\n]+)`")

    /**
     * `[文字](url)` 行内链接：**自动识别**（用户已确认）。
     *
     * 自动意味着"正文里字面的 `[见](x)` 会被当成链接"，所以落地前专门查过现有数据：
     * 全部笔记里含 `](` 的块数为 **0**，即这条选择不会改写任何既有内容
     * （设计文档 §13.3）。
     */
    private val inlineLink = Regex("\\[([^\\]\\n]+)]\\(([^)\\n]+)\\)")

    /**
     * 解析所有行内原子，按起点排序。
     *
     * 三类之间**不会互相包含**（`$…$` 里写反引号是罕见写法，交给"先到先得"即可），
     * 所以这里不做重叠裁剪，只保证有序。
     */
    fun of(text: String): List<InlineNode> {
        if (text.isEmpty()) return emptyList()
        val nodes = mutableListOf<InlineNode>()
        if (text.contains('$')) {
            // 复用 MathSpans 的区间判定：它已经处理了"块级优先、行内不重复计入"
            MathSpans.ranges(text).forEach {
                nodes += InlineNode(InlineKind.MATH, it.first, it.last + 1)
            }
        }
        if (text.contains('`')) {
            inlineCode.findAll(text).forEach {
                nodes += InlineNode(InlineKind.CODE, it.range.first, it.range.last + 1)
            }
        }
        if (text.contains("](")) {
            inlineLink.findAll(text).forEach {
                nodes += InlineNode(InlineKind.LINK, it.range.first, it.range.last + 1)
            }
        }
        return nodes.sortedBy { it.start }
    }

    /** 光标落在哪个原子里（含两端，便于"在公式末尾继续打字"）。 */
    fun containing(nodes: List<InlineNode>, offset: Int): InlineNode? =
        nodes.firstOrNull { it.contains(offset) }

    /**
     * 需要**隐藏源码**的原子：只有公式，且**除了光标所在的那一条**。
     *
     * 行内代码不隐藏 —— 它就地套等宽样式即可，没有"盒宽与图不符"的问题，
     * 也就不用参与覆盖层与控盒那一套。光标进入公式时它必须恢复成可编辑源码（Notion 式），
     * 否则用户改不了公式里的错字，而且折叠态下光标也没有落脚点。
     */
    fun hiddenMath(nodes: List<InlineNode>, activeOffset: Int?): List<InlineNode> =
        nodes.filter { it.kind == InlineKind.MATH }
            .filterNot { activeOffset != null && it.contains(activeOffset) }

    /** 选区两端**向外吸附**到原子边界（公式与行内代码都算）。 */
    fun snapOutside(text: String, start: Int, end: Int): Pair<Int, Int> {
        if (start >= end) return start to end
        val nodes = of(text)
        var from = start
        var to = end
        nodes.firstOrNull { from > it.start && from < it.end }?.let { from = it.start }
        nodes.firstOrNull { to > it.start && to < it.end }?.let { to = it.end }
        return from to to
    }
}
