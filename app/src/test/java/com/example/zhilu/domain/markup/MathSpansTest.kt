package com.example.zhilu.domain.markup

import com.example.zhilu.domain.model.EmphasisTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 公式区间与行内标记的边界（真机反馈：「给公式标注特殊颜色导致原公式失效」）。
 *
 * 两条硬约束：
 * 1. 物化**不能**在公式里转义花括号 —— `_{\text{发}}` 写成 `_{\text{发\}}}` 会让 LaTeX 解析失败；
 * 2. 落在公式里的标记必须丢掉 —— `{{k:…}}` 在 LaTeX 里没有合法形态。
 */
class MathSpansTest {

    private val formula = "\$W_{\\text{发}} \\le 2^n - 1\$"

    // ---- MathSpans ----

    @Test
    fun `识别行内与块级公式`() {
        val text = "代入 \$a+b\$ 与 \$\$c^2\$\$ 即可"
        val ranges = MathSpans.ranges(text)
        assertEquals(2, ranges.size)
        assertEquals("\$a+b\$", text.substring(ranges[0].first, ranges[0].last + 1))
        assertEquals("\$\$c^2\$\$", text.substring(ranges[1].first, ranges[1].last + 1))
    }

    @Test
    fun `块级公式内部不再被行内规则切一刀`() {
        assertEquals(1, MathSpans.ranges("\$\$x \$ y\$\$").size)
    }

    @Test
    fun `没有美元符号时不算公式`() {
        assertTrue(MathSpans.ranges("普通文本 {{k:要点}}").isEmpty())
        assertFalse(MathSpans.intersects("价格 5\$ 一件", 0, 4))
    }

    @Test
    fun `相交判定覆盖选区横跨公式两端的情况`() {
        val text = "前\$a+b\$后"
        val start = text.indexOf('$')
        assertTrue(MathSpans.intersects(text, start - 1, start + 2))
        assertTrue(MathSpans.intersects(text, start, start + 5))
        assertFalse(MathSpans.intersects(text, 0, start))
    }

    @Test
    fun `去定界符保留源码`() {
        assertEquals(
            "· n = 编号比特数",
            MathSpans.stripDelimiters("· \$n\$ = 编号比特数")
        )
        assertEquals("x^2 + y^2", MathSpans.stripDelimiters("\$\$x^2 + y^2\$\$"))
    }

    // ---- 物化：不破坏公式 ----

    @Test
    fun `公式里的花括号不被转义`() {
        // 这是"标注颜色导致公式失效"的直接回归点：以前会输出 `_{\text{发\}}}`
        assertEquals(formula, InlineMarkup.materialize(formula, emptyList()))
        assertFalse(InlineMarkup.materialize(formula, emptyList()).contains("\\}"))
    }

    @Test
    fun `公式外的花括号照旧转义`() {
        assertEquals("a\\{{b\\}}c", InlineMarkup.materialize("a{{b}}c", emptyList()))
    }

    @Test
    fun `落在公式里的标记被丢弃`() {
        val text = "见 \$a+b\$ 说明"
        val start = text.indexOf('$')
        val span = InlineSpan(EmphasisTone.KEY, InlineBrush.HIGHLIGHT, start + 1, start + 3)
        assertEquals(text, InlineMarkup.materialize(text, listOf(span)))
    }

    @Test
    fun `公式外的标记不受影响`() {
        val text = "见 \$a+b\$ 说明"
        val span = InlineSpan(EmphasisTone.KEY, InlineBrush.HIGHLIGHT, 0, 1)
        assertEquals("{{k:见}} \$a+b\$ 说明", InlineMarkup.materialize(text, listOf(span)))
    }

    @Test
    fun `普通文本的标记行为不变`() {
        val text = "要点在这里"
        val span = InlineSpan(EmphasisTone.KEY, InlineBrush.HIGHLIGHT, 0, 2)
        assertEquals("{{k:要点}}在这里", InlineMarkup.materialize(text, listOf(span)))
    }

    // ---- 修复既有破损内容 ----

    @Test
    fun `被转义过的公式在规整后恢复`() {
        val damaged = "GBN 允许 \$W_{\\text{发\\}} \\le 2^3 - 1 = 7\$"
        val normalized = InlineMarkupNormalizer.normalize(damaged)
        // 规整会**修好**这处转义（而不是原样保留）：公式里不应再出现 `\}}`
        assertEquals("GBN 允许 \$W_{\\text{发}} \\le 2^3 - 1 = 7\$", normalized)
    }

    @Test
    fun `被织进公式的标记在规整后消失且公式完好`() {
        // 真机上真实写坏的内容（block 857）
        val damaged = "· GBN：代入 \$W_{\\text{收\\}}=1\$ → {{w:\$W_}}{{{w:\\text}}{{{w:发}}\\}}{{w: \\le 2^n - 1\$}}"
        val normalized = InlineMarkupNormalizer.normalize(damaged)
        assertEquals(
            "· GBN：代入 \$W_{\\text{收}}=1\$ → \$W_{\\text{发}} \\le 2^n - 1\$",
            normalized
        )
    }

    @Test
    fun `纯文本出口同时去掉标记与公式定界符`() {
        assertEquals("n = 编号比特数", InlineMarkup.toPlainText("\$n\$ = 编号比特数"))
        assertEquals("要点在这里", InlineMarkup.toPlainText("{{k:要点}}**在这里**"))
        assertEquals("调用 foo() 即可", InlineMarkup.toPlainText("调用 `foo()` 即可"))
        // 链接只留文字：摘要/预览是纯 Text，渲染不了链接样式，漏出 URL 会很难看
        assertEquals(
            "看 文档 即可",
            InlineMarkup.toPlainText("看 [文档](https://example.com/a) 即可")
        )
    }

    // ---- 选区绕开公式（公式当原子对象）----

    @Test
    fun `选区端点落在公式内时被推出公式`() {
        val text = "前\$a+b\$后"
        val start = text.indexOf('$')
        // 终点停在公式中间 → 推到公式之后
        assertEquals(0 to start + 5, MathSpans.snapOutside(text, 0, start + 2))
        // 起点落在公式中间 → 退到公式之前
        assertEquals(start to text.length, MathSpans.snapOutside(text, start + 1, text.length))
    }

    @Test
    fun `框选文字加公式时只标公式之外的段`() {
        val text = "甲\$a+b\$乙"
        val start = text.indexOf('$')
        val segments = MathSpans.outsideSegments(text, 0, text.length)
        assertEquals(2, segments.size)
        assertEquals("甲", text.substring(segments[0].first, segments[0].second))
        assertEquals("乙", text.substring(segments[1].first, segments[1].second))
    }

    @Test
    fun `整段选区都在公式里时没有可标记的段`() {
        val text = "前\$a+b\$后"
        val start = text.indexOf('$')
        assertTrue(MathSpans.outsideSegments(text, start, start + 5).isEmpty())
    }

    @Test
    fun `绕开公式后物化出的标记不动公式`() {
        val text = "甲\$W_{\\text{发}}\$乙"
        val start = text.indexOf('$')
        val segments = MathSpans.outsideSegments(text, 0, text.length)
        val spans = segments.fold(emptyList<InlineSpan>()) { acc, (from, to) ->
            InlineSpanAdjuster.apply(acc, from, to, EmphasisTone.KEY, InlineBrush.HIGHLIGHT, text)
        }
        val stored = InlineMarkup.materialize(text, spans)
        assertEquals("{{k:甲}}\$W_{\\text{发}}\${{k:乙}}", stored)
        // 关键：公式源码一字未动（没有 \\}} 之类的转义）
        assertEquals(text.replace("甲", "").replace("乙", ""), stored.replace("{{k:甲}}", "").replace("{{k:乙}}", ""))
    }

    // ---- 整条公式可以被标注（扩展后的标记体允许 $…$）----

    @Test
    fun `canCover 只放行整条公式`() {
        val text = "ab\$c{d}e\$fg"
        val ranges = MathSpans.ranges(text)
        assertFalse("切进公式内部不允许", MathSpans.canCover(ranges, 1, 5))
        assertTrue("正好包住整条公式", MathSpans.canCover(ranges, 2, 9))
        assertTrue("两端都超出公式", MathSpans.canCover(ranges, 0, text.length))
        assertTrue("完全不碰公式", MathSpans.canCover(ranges, 0, 2))
    }

    @Test
    fun `整条公式可以被标记包住且能原样读回`() {
        val text = "见 \$W_{\\text{发}} \\le 2^{n-1}\$ 说明"
        val start = text.indexOf('$')
        val end = text.lastIndexOf('$') + 1
        val stored = InlineMarkup.materialize(
            text,
            listOf(InlineSpan(EmphasisTone.KEY, InlineBrush.HIGHLIGHT, start, end))
        )
        assertEquals("见 {{k:\$W_{\\text{发}} \\le 2^{n-1}\$}} 说明", stored)

        val parsed = InlineMarkup.parseSpans(stored)
        assertEquals(text, parsed.visibleText)
        assertEquals(1, parsed.spans.size)
        assertEquals(start, parsed.spans[0].start)
        assertEquals(end, parsed.spans[0].end)
    }

    @Test
    fun `选区落在公式内时向外吸附成整条公式`() {
        val text = "前\$a+b\$后"
        val start = text.indexOf('$')
        // 只框到公式中间的一小段 → 吸附成整条公式
        assertEquals(start to (start + 5), MathSpans.snapOutside(text, start + 1, start + 3))
    }

    @Test
    fun `半截公式的选区吸附后仍不切坏公式`() {
        val text = "前\$W_{\\text{发}}\$后"
        val start = text.indexOf('$')
        val (from, to) = MathSpans.snapOutside(text, start, start + 4)
        // 吸附后要么整条，要么在外侧；反正物化出来的东西必须能读回同样的可见文本
        val stored = InlineMarkup.materialize(
            text,
            listOf(InlineSpan(EmphasisTone.KEY, InlineBrush.HIGHLIGHT, from, to))
        )
        assertEquals(text, InlineMarkup.parseSpans(stored).visibleText)
    }
}
