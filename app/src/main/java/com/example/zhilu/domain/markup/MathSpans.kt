package com.example.zhilu.domain.markup

/**
 * 公式区间：`$$…$$`（块级）与 `$…$`（行内、不跨行）。
 *
 * **行内标记层必须把公式当成不透明区域**，否则就是真机反馈的那两个 bug：
 * - 物化时在公式里转义花括号 —— `_{\text{发}}` 被写成 `_{\text{发\}}}`，
 *   LaTeX 随即解析失败、退化成源码显示（"标注颜色导致原公式失效"）；
 * - 把 `{{k:…}}` 织进公式内部 —— 标记在公式里没有任何合法形态，
 *   只能得到 `{{w:$W_}}{{{w:\text}}…` 这种再也读不回来的碎片。
 *
 * 纯函数、不依赖 Compose。
 */
object MathSpans {

    /** `$$…$$` 块级公式（先于行内处理，允许跨行）。 */
    private val blockMath = Regex("\\$\\$[^$]+\\$\\$")

    /** `$…$` 行内公式；不跨行。 */
    private val inlineMath = Regex("\\$([^$\\n]+)\\$")

    /**
     * 文本里所有公式区间，升序返回。
     *
     * 先取块级再取行内，并剔除落在块级区间内的行内命中
     * —— 否则 `$$…$$` 的内部会被行内规则再切一刀。
     */
    fun ranges(text: String): List<IntRange> {
        if (!text.contains('$')) return emptyList()
        val blocks = blockMath.findAll(text).map { it.range }.toList()
        val inlines = inlineMath.findAll(text)
            .map { it.range }
            .filter { inline -> blocks.none { inline.first >= it.first && inline.last <= it.last } }
        return (blocks + inlines).sortedBy { it.first }
    }

    /** `[start, end)` 是否与任何公式相交（空区间不算）。 */
    fun intersects(text: String, start: Int, end: Int): Boolean {
        if (start >= end) return false
        return ranges(text).any { start <= it.last && it.first < end }
    }

    /** 同上，但复用已经算好的区间表。 */
    fun intersects(ranges: List<IntRange>, start: Int, end: Int): Boolean {
        if (start >= end) return false
        return ranges.any { start <= it.last && it.first < end }
    }

    /**
     * 标记区间 [start, end) 是否**可存**。
     *
     * 规则：与公式相交时，必须把整条公式完整包住；**切进公式内部**一律不允许
     * （`{{k:…}}` 的边界会与 LaTeX 的花括号打架，写出来就是读不回来的碎片）。
     */
    fun canCover(ranges: List<IntRange>, start: Int, end: Int): Boolean =
        ranges.none { range ->
            val intersects = start <= range.last && range.first < end
            val coversWhole = start <= range.first && end >= range.last + 1
            intersects && !coversWhole
        }

    /** 下标是否落在某个公式区间内。 */
    fun contains(ranges: List<IntRange>, index: Int): Boolean =
        ranges.any { index >= it.first && index <= it.last }

    /**
     * 把选区两端**推出**公式。
     *
     * 起点落在公式内部 → 退到公式之前；终点落在公式内部 → 进到公式之后。
     * 这样"从公式左边拖到公式中间"会变成"只标公式左边那段"，而不是把标记织进 LaTeX。
     */
    fun snapOutside(text: String, start: Int, end: Int): Pair<Int, Int> {
        if (start >= end) return start to end
        val math = ranges(text)
        var from = start
        var to = end
        math.firstOrNull { from > it.first && from <= it.last }?.let { from = it.first }
        math.firstOrNull { to > it.first && to <= it.last }?.let { to = it.last + 1 }
        return from to to
    }

    /**
     * 选区**排除公式之后**剩下的连续段。
     *
     * 框选"文字 A + 公式 + 文字 B"时应当得到 A、B 两段：公式保持原样不被上色，
     * 两边的文字照常标记。返回空表表示整段选区都在公式里（此时只能提示用户）。
     */
    fun outsideSegments(text: String, start: Int, end: Int): List<Pair<Int, Int>> {
        if (start >= end) return emptyList()
        val math = ranges(text)
        val segments = mutableListOf<Pair<Int, Int>>()
        var cursor = start
        var index = start
        while (index < end) {
            val range = math.firstOrNull { index >= it.first && index <= it.last }
            if (range == null) {
                index++
                continue
            }
            if (index > cursor) segments += cursor to index
            index = minOf(range.last + 1, end)
            cursor = index
        }
        if (cursor < end) segments += cursor to end
        return segments
    }

    /**
     * 去掉公式定界符、保留源码。
     *
     * 给"渲染不了公式图片"的纯文本出口用（列表摘要、卡片摘要）：
     * 原样显示会得到 `· $n$ = 编号比特数` 这种半成品（真机反馈的"未转义"）。
     */
    fun stripDelimiters(text: String): String {
        if (!text.contains('$')) return text
        // 用 removeSurrounding 而不是捕获组：正则里加不加分组都不影响这里
        var result = blockMath.replace(text) { it.value.removeSurrounding("$$") }
        result = inlineMath.replace(result) { it.value.removeSurrounding("$") }
        return result
    }
}
