package com.example.zhilu.domain.markup

import com.example.zhilu.domain.model.EmphasisTone

/**
 * 行内标记的笔触（对应语法里的后缀）。
 *
 * 笔触写在语法里、逐处指定，而不是"全局渲染偏好"——
 * 否则同一段文本在不同设置下形态不同，渲染就依赖了外部状态。
 */
enum class InlineBrush(val suffix: String) {
    /** 荧光笔：底色 20% + 加深墨色 + 半粗。默认笔触，语法不写后缀。 */
    HIGHLIGHT(""),

    /** 纯变色：只改字色，无底色，最克制。 */
    COLOR("-c"),

    /** 下划线：变色 + 底部同色线。 */
    UNDERLINE("-u"),

    /**
     * 加粗（设计文档 §10 P3）。
     *
     * 加粗**不是语义角色**（它不分类内容，只改字重），所以它没有 `{{}}` 语法，
     * 走的是既有的 `**…**` 那一套。这里把它做成一种"笔触"而不是新概念，是刻意的：
     * [InlineSpanAdjuster] 的 8 条编辑规则、`normalize` 的重叠裁剪、`apply`/`clear`
     * 全都只关心 `start`/`end`，于是**整套 span 代数一行都不用改**，
     * 加粗立刻获得与语义标记同等的编辑行为（划词加粗、随文字增删自动伸缩、跨行断开）。
     *
     * 代价：`InlineSpan.tone` 对加粗无意义（沿用默认角色，渲染时忽略）。
     * 把 `tone` 改成可空会让 30 多处构造点全部需要处理空值，
     * 而它们正是最容易出 bug 的地方 —— 不值得。
     *
     * [suffix] 取 `-b` 只是占位：加粗永远不会被物化成 `{{?-b:…}}`。
     */
    BOLD("-b");

    companion object {
        fun fromSuffix(suffix: String?): InlineBrush =
            entries.firstOrNull { it.suffix == (suffix ?: "") && it != BOLD } ?: HIGHLIGHT
    }
}

/**
 * 一条行内标记，坐标用**可见文本**（即剥离所有语法字符后的正文）的下标表示。
 *
 * 会话内派生物：随时可以从 `content` 重新解析出来，从不落库。
 */
data class InlineSpan(
    val tone: EmphasisTone,
    val brush: InlineBrush = InlineBrush.HIGHLIGHT,
    val start: Int,
    val end: Int
) {
    val isCollapsed: Boolean get() = end <= start

    val length: Int get() = (end - start).coerceAtLeast(0)

    fun coversText(): Boolean = end > start

    fun contains(offset: Int): Boolean = offset in start until end
}

/**
 * 一次文本编辑对 span 表的影响。
 *
 * 这是"标记不进编辑缓冲"方案里唯一需要严谨定义的地方，因此做成纯函数并穷举单测。
 * 规则见设计文档 §3.11.2：
 *
 * 1. 在 span 内部插入 → 扩展
 * 2. 在 span 末尾插入（caret == end）→ 扩展（"我在接着写这个要点"）
 * 3. 在 span 起始插入（caret == start）→ 不扩展，内容整体右移
 * 4. 删除与 span 相交 → 按交叠长度收缩
 * 5. 删除完全覆盖 span → 删除该 span
 * 6. 零长 span（「标记中」）处的输入 → 扩展（吸收输入）
 * 7. 换行 → span 在换行处断开（内联标记不跨行）
 * 8. 全选删除 → span 全清（规则 5 的自然结果）
 */
object InlineSpanAdjuster {

    /**
     * 比较编辑前后的可见文本，推出新的 span 表。
     *
     * 只做一次"公共前缀 + 公共后缀"的差分，就足以定位这次编辑替换了哪一段——
     * 文本输入框的每次变更都是单点/单段替换，这个假设对 `BasicTextField` 成立。
     */
    fun adjust(spans: List<InlineSpan>, previousText: String, nextText: String): List<InlineSpan> {
        val (changeStart, changeEnd, insertedLength) = diff(previousText, nextText)
        // 插入点上若有一条零长标记（「标记中」），说明用户是**特意**要在这一点起一个新标记。
        // 此时必须关掉规则 2（"末尾插入即扩展"）：否则紧邻的前一条 span 会扩过来覆盖新文字，
        // 随后 normalize 的"先到先得"再把新标记整条丢掉 —— 表现就是
        // "光标停在已标记文字后面，点另一个角色/加粗再打字，新标记凭空消失"（真机踩过）。
        val pendingAtInsert = insertedLength > 0 &&
            changeEnd == changeStart &&
            spans.any { it.isCollapsed && it.start == changeStart }
        val shifted = spans.mapNotNull {
            shift(
                span = it,
                changeStart = changeStart,
                changeEnd = changeEnd,
                insertedLength = insertedLength,
                allowExtend = !pendingAtInsert
            )
        }
        return normalize(shifted, nextText)
    }

    /** 编辑替换区间：`previous[changeStart, changeEnd)` 被换成 `insertedLength` 个新字符。 */
    data class Edit(val changeStart: Int, val changeEnd: Int, val insertedLength: Int)

    fun diff(previousText: String, nextText: String): Edit {
        val maxPrefix = minOf(previousText.length, nextText.length)
        var prefix = 0
        while (prefix < maxPrefix && previousText[prefix] == nextText[prefix]) prefix++

        var suffix = 0
        val maxSuffix = minOf(previousText.length, nextText.length) - prefix
        while (
            suffix < maxSuffix &&
            previousText[previousText.length - 1 - suffix] == nextText[nextText.length - 1 - suffix]
        ) {
            suffix++
        }

        val changeEnd = previousText.length - suffix
        val insertedLength = nextText.length - suffix - prefix
        return Edit(changeStart = prefix, changeEnd = changeEnd, insertedLength = insertedLength)
    }

    private fun shift(
        span: InlineSpan,
        changeStart: Int,
        changeEnd: Int,
        insertedLength: Int,
        /** 见 [adjust]：插入点上有「标记中」时为 false。 */
        allowExtend: Boolean
    ): InlineSpan? {
        val removed = changeEnd - changeStart
        val delta = insertedLength - removed
        val pureInsert = removed == 0 && insertedLength > 0

        // 规则 6：零长 span 在光标处吸收输入（「标记中」）
        if (span.isCollapsed && pureInsert && changeStart == span.start) {
            return span.copy(start = span.start, end = span.start + insertedLength)
        }

        // 规则 3 / 常规右移：编辑完全落在 span 之前
        if (changeEnd <= span.start) {
            return span.copy(start = span.start + delta, end = span.end + delta)
        }

        // 编辑完全落在 span 之后
        if (changeStart >= span.end) {
            // 规则 2：末尾插入即扩展
            if (pureInsert && changeStart == span.end && allowExtend) {
                return span.copy(end = span.end + insertedLength)
            }
            return span
        }

        // 相交
        val overlap = minOf(span.end, changeEnd) - maxOf(span.start, changeStart)
        val removedInside = overlap.coerceAtLeast(0)
        val insertedInside = if (changeStart > span.start && changeStart < span.end) insertedLength else 0
        val newStart = if (changeStart <= span.start) changeStart else span.start
        val newEnd = span.end - removedInside + insertedInside

        // 规则 5：被完全覆盖 → 丢弃
        if (newEnd <= newStart) return null
        return span.copy(start = newStart, end = newEnd)
    }

    /**
     * 收尾：裁到合法范围、按起点排序、去重叠、断开跨行的 span（规则 7）。
     *
     * **零长 span 必须保留** —— 它是「标记中」的状态载体（"下一步输入要落进这里"）。
     * 在这里把它当成"空区间"过滤掉，`commit` 一提交就会丢掉待标记状态：
     * 用户点了色块、接着打字却没有颜色，数据库里也没有标记（真机上踩过）。
     * 真正要丢弃零长标记的地方只有两处：`materialize`（不落库）与 [dropCollapsed]（显式结束）。
     */
    fun normalize(spans: List<InlineSpan>, visibleText: String): List<InlineSpan> {
        val limit = visibleText.length
        // 先记下"原本就是零长"这件事：裁剪后长度会变，靠 isCollapsed 判断会误伤 ——
        // 被编辑削成 0 长度的普通标记不属于「标记中」，必须丢掉。
        val prepared = spans
            .map { original ->
                original.isCollapsed to original.copy(
                    start = original.start.coerceIn(0, limit),
                    end = original.end.coerceIn(0, limit)
                )
            }
            .sortedWith(compareBy({ it.second.start }, { it.second.end }))

        val result = mutableListOf<InlineSpan>()
        var lastEnd = -1
        for ((wasCollapsed, span) in prepared) {
            if (wasCollapsed) {
                // 零长标记原样保留，不参与重叠裁剪
                result += span
                continue
            }
            val start = maxOf(span.start, lastEnd)
            if (span.end <= start) continue
            if (start != span.start) {
                // 与前一条重叠：截掉重叠部分，重叠区归前一条（先到先得，不做样式叠加）
                result += span.copy(start = start, end = span.end)
            } else {
                result += span
            }
            lastEnd = result.last().end
        }
        return result.flatMap { splitAtNewlines(it, visibleText) }
    }

    /** 内联标记不跨行：跨行时在换行处断开，每行一段。 */
    private fun splitAtNewlines(span: InlineSpan, visibleText: String): List<InlineSpan> {
        if (span.isCollapsed) return listOf(span)
        val slice = visibleText.substring(span.start, span.end)
        if (!slice.contains('\n')) return listOf(span)

        val pieces = mutableListOf<InlineSpan>()
        var pieceStart = span.start
        for (index in span.start until span.end) {
            if (visibleText[index] == '\n') {
                if (index > pieceStart) pieces += span.copy(start = pieceStart, end = index)
                pieceStart = index + 1
            }
        }
        if (span.end > pieceStart) pieces += span.copy(start = pieceStart, end = span.end)
        return pieces
    }

    // ---- 供工具条使用的小操作 ----

    /** 光标/选区落在哪条标记里（用于工具条的状态回显）。取第一条命中的。 */
    fun spanAt(spans: List<InlineSpan>, offset: Int): InlineSpan? =
        spans.firstOrNull { it.contains(offset) }

    /** 选区是否被同一条 span 完整覆盖（决定"再点同色 = 取消"是否成立）。 */
    fun spanCovering(spans: List<InlineSpan>, start: Int, end: Int): InlineSpan? =
        spans.firstOrNull { it.start <= start && it.end >= end && it.end > it.start }

    /**
     * 给选区上色 / 换色 / 换笔触。已有标记先清除再写入，保证语义是"这一段就是这个角色"。
     */
    fun apply(
        spans: List<InlineSpan>,
        start: Int,
        end: Int,
        tone: EmphasisTone,
        brush: InlineBrush,
        visibleText: String
    ): List<InlineSpan> {
        if (end <= start) return spans
        val cleared = clear(spans, start, end, visibleText)
        return normalize(cleared + InlineSpan(tone, brush, start, end), visibleText)
    }

    /**
     * 清除选区内的标记：只删掉被覆盖的部分，保留选区之外的残缺段。
     */
    fun clear(
        spans: List<InlineSpan>,
        start: Int,
        end: Int,
        visibleText: String
    ): List<InlineSpan> {
        if (end <= start) return spans
        val result = mutableListOf<InlineSpan>()
        for (span in spans) {
            if (span.end <= start || span.start >= end) {
                result += span
                continue
            }
            // 选区左侧残留
            if (span.start < start) result += span.copy(end = start)
            // 选区右侧残留
            if (span.end > end) result += span.copy(start = end)
        }
        return normalize(result, visibleText)
    }

    /** 在光标处放一条零长标记（「标记中」）。同时移除光标处原有的零长标记。 */
    fun startPending(
        spans: List<InlineSpan>,
        offset: Int,
        tone: EmphasisTone,
        brush: InlineBrush
    ): List<InlineSpan> = spans.filterNot { it.isCollapsed } + InlineSpan(tone, brush, offset, offset)

    /** 丢弃所有零长标记（结束「标记中」且未输入任何内容）。 */
    fun dropCollapsed(spans: List<InlineSpan>): List<InlineSpan> = spans.filter { it.coversText() }
}
