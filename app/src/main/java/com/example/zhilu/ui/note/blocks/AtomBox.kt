package com.example.zhilu.ui.note.blocks

import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.min

/**
 * 行内公式图的**绘制尺寸规则** —— 纯函数，集中在这里，由单测锁住。
 *
 * ## 这个文件为什么存在（以及为什么它这么小）
 *
 * 公式在编辑态是这样画出来的：不可见源码（透明）占住排版位置，覆盖层再在这个空位里补画公式图。
 * 于是一个自然的想法是"让源码占的盒子等于公式图尺寸" —— 这样公式前后都不会有多余空白。
 * 那一版实现过，也踩过：它必须靠 **「量盒宽 → 改源码字距 → 再布局 → 再量」的反馈环** 才能收敛，
 * 而这条路在真机上引出了整串问题（字距 sp/px 串了单位被夹成 +16sp、把行尾换行符当成折行判据、
 * 按文本偏移做的样式表被重新排版清空）。量化之后收益只有 **17~50px 的留白**，
 * 不值得为它保留一条需要收敛证明的回路（设计文档 §14.6 记了整段排查过程）。
 *
 * 所以现在的规则是**一次算完、不再回头**：
 *
 * 1. **只缩小，绝不放大** —— 只读态画的就是图的天然尺寸（占位高 = 图高），
 *    放大等于同一个公式在两种状态下两个大小；
 * 2. **两个上限里更紧的那个说了算** —— 该行剩余宽度（超出去会压到后面的文字）
 *    与行盒高度（超出去会压到相邻行）；
 * 3. 代价：源码天然比图宽时，公式后面会留一段空白。这是**可见的留白**，
 *    比"公式被压小 / 画错位置"划算；长公式有「转为公式块」这个出口。
 */
internal object AtomBox {

    /** 覆盖层绘制的最小缩放：再小就等于看不见了，宁可让它溢出也不能画没了。 */
    const val MIN_DRAW_SCALE = 0.25f

    /**
     * 公式图允许比行盒高出多少（倍数）。
     *
     * 为什么不是严格的 1.0：只读态里"**行的视觉高度就是图高**"（占位高 = 图高），
     * 所以同一条公式在编辑态严格贴行盒就会被压小一圈 —— 实测含 CJK 的那条是 98px 图 vs 82px 行盒，
     * 压到 84%，看着就比只读态小（§14.5 报的就是这个观感）。
     *
     * 1.25 是折中：文字的上下本来就各有 ascent/descent 的余量，图越过行盒边界一点不会碰到相邻行的字；
     * 而真正超高的公式（`\sum` 那类，图高是行盒的 2 倍以上）仍然老老实实按行盒缩。
     */
    const val MAX_HEIGHT_OVERFLOW = 1.25f

    /**
     * 覆盖层把公式图画多大。
     *
     * **只缩小、不放大**，两个上限里更紧的那个说了算：
     * - `availableWidthPx`：该行从原子起点算起还剩多宽（超出会压到后面的文字）；
     * - `availableHeightPx × MAX_HEIGHT_OVERFLOW`：行盒高度留一点溢出余量（超出会压到相邻行）。
     */
    fun drawScale(
        imageWidthPx: Int,
        imageHeightPx: Int,
        availableWidthPx: Float,
        availableHeightPx: Float
    ): Float {
        if (imageWidthPx <= 0 || imageHeightPx <= 0) return 1f
        if (availableWidthPx <= 0f || availableHeightPx <= 0f) return 1f
        val widthFit = availableWidthPx / imageWidthPx
        val heightFit = (availableHeightPx * MAX_HEIGHT_OVERFLOW) / imageHeightPx
        return min(1f, min(widthFit, heightFit)).coerceAtLeast(MIN_DRAW_SCALE)
    }

    /** 绘制尺寸（px），用于诊断与单测。 */
    fun drawSize(
        imageWidthPx: Int,
        imageHeightPx: Int,
        availableWidthPx: Float,
        availableHeightPx: Float
    ): Pair<Float, Float> {
        val scale = drawScale(imageWidthPx, imageHeightPx, availableWidthPx, availableHeightPx)
        return imageWidthPx * scale to imageHeightPx * scale
    }
}

/**
 * 一个原子在布局里量到的横向占位。
 *
 * @param leftPx 原子第一个字符的左边界
 * @param rightPx 原子之后那个插入点的左边界（同一行时 = 原子的右边界）
 * @param wrapped 原子是否跨了行
 */
internal data class AtomRunMetrics(
    val leftPx: Float,
    val rightPx: Float,
    val wrapped: Boolean
) {
    val widthPx: Float get() = rightPx - leftPx
}

/**
 * 量出原子在 [layout] 里真正占的横向空间，并判断它有没有跨行。
 *
 * 用**两个光标矩形**：`cursorRect(start)` 是原子第一个字符的插入点，`cursorRect(end)` 是原子
 * 之后那个插入点；两者在同一行时，差值就是原子的排版宽度（实测 494px，与其他原子的位置自洽）。
 *
 * 两个坑都踩过，别再走回去：
 *
 * 1. **`getBoundingBox` 不可靠**：在 `BasicTextField` 的算子里它对某些偏移直接给不出包围盒
 *    （实测 29 字符的原子取 `end - 1` 就失败），于是原子被当成"取不到 → 折行"；
 * 2. **`cursorRect(end)` 不是"原子右边界"**：原子正好结束在行尾时它取到的是行尾**换行符**
 *    的位置，矩形落到下一行行首 —— 单行原子于是被误判成折行。
 *    判据必须看 `top`（换行符的 top 在下一行），不能看 `left` 的大小关系。
 *
 * @return null = 起点矩形取不到（调用方按"量不出"处理，不要当成折行）
 */
internal fun atomRunMetrics(
    layout: TextLayoutResult,
    start: Int,
    end: Int
): AtomRunMetrics? {
    if (end <= start) return null
    val from = runCatching { layout.getCursorRect(start) }.getOrNull() ?: return null
    val to = runCatching { layout.getCursorRect(end) }.getOrNull()
    if (to == null) return AtomRunMetrics(from.left, from.left, wrapped = true)
    val sameLine = kotlin.math.abs(to.top - from.top) < 0.5f
    return AtomRunMetrics(
        leftPx = from.left,
        rightPx = if (sameLine) to.left else from.left,
        wrapped = !sameLine
    )
}

/**
 * 该原子所在那一行的可用内容宽度（从原子起点往右还剩多少）。
 *
 * 这是绘制时唯一合理的宽度上限：原子的**排版宽度属于不可见源码**（可以比图宽得多），
 * 拿它当上限会把公式压小甚至压出边界 —— 那正是 §14.6 那个坑。
 */
internal fun atomLineWidth(layout: TextLayoutResult, start: Int, fromLeftPx: Float): Float {
    val line = runCatching { layout.getLineForOffset(start) }.getOrNull() ?: return 0f
    val right = runCatching { layout.getLineRight(line) }.getOrNull() ?: return 0f
    return (right - fromLeftPx).coerceAtLeast(0f)
}
