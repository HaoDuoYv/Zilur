package com.example.zhilu.domain.markup

import com.example.zhilu.domain.model.EmphasisTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 加粗进 span 表（设计文档 §10 P3）。
 *
 * 验收口径：加粗与语义标记**共用同一套 span 代数**，所以它不仅"能被解析"，
 * 还必须满足与语义标记完全相同的往返与编辑性质。
 */
class InlineBoldTest {

    @Test
    fun `解析出加粗 span`() {
        val parsed = InlineMarkup.parseSpans("正定**必须**成立")
        assertEquals("正定必须成立", parsed.visibleText)
        assertEquals(1, parsed.spans.size)
        assertEquals(InlineBrush.BOLD, parsed.spans[0].brush)
        assertEquals(2, parsed.spans[0].start)
        assertEquals(4, parsed.spans[0].end)
    }

    @Test
    fun `加粗物化回原文（往返一致）`() {
        val original = "正定**必须**成立"
        val parsed = InlineMarkup.parseSpans(original)
        assertEquals(original, InlineMarkup.materialize(parsed.visibleText, parsed.spans))
    }

    @Test
    fun `加粗与语义标记共存且互不吃掉`() {
        val original = "**重点**是{{w:注意}}顺序"
        val parsed = InlineMarkup.parseSpans(original)
        assertEquals("重点是注意顺序", parsed.visibleText)
        assertEquals(2, parsed.spans.size)
        assertEquals(InlineBrush.BOLD, parsed.spans[0].brush)
        assertEquals(EmphasisTone.WARN, parsed.spans[1].tone)
        assertEquals(InlineBrush.HIGHLIGHT, parsed.spans[1].brush)
        assertEquals(original, InlineMarkup.materialize(parsed.visibleText, parsed.spans))
    }

    @Test
    fun `stripMarkup 也剥掉加粗定界符`() {
        assertEquals("重点是什么", InlineMarkup.stripMarkup("**重点**是什么"))
    }

    @Test
    fun `没有闭合的加粗保持字面量`() {
        val parsed = InlineMarkup.parseSpans("3 ** 4 等于 12")
        assertTrue(parsed.spans.isEmpty())
        assertEquals("3 ** 4 等于 12", parsed.visibleText)
    }

    @Test
    fun `跨行的加粗不成标记`() {
        val parsed = InlineMarkup.parseSpans("**第一行\n第二行**")
        assertTrue(parsed.spans.isEmpty())
        assertEquals("**第一行\n第二行**", parsed.visibleText)
    }

    @Test
    fun `空内容不成标记`() {
        val parsed = InlineMarkup.parseSpans("****")
        assertTrue(parsed.spans.isEmpty())
        assertEquals("****", parsed.visibleText)
    }

    @Test
    fun `内容含花括号时按字面量处理，不吞字符`() {
        // 花括号是 {{}} 语法的地盘，边界有歧义 → 整段不成加粗，星号原样留在可见文本里
        // （与解析器一贯的"非法写法原样显示"一致，绝不吞字符）
        val text = "**{{k:要点}}**"
        val parsed = InlineMarkup.parseSpans(text)
        assertTrue(parsed.spans.none { it.brush == InlineBrush.BOLD })
        assertEquals("**要点**", parsed.visibleText)
        // 内层语义标记照常生效
        assertEquals(EmphasisTone.KEY, parsed.spans.single().tone)
        // 再物化一次仍然稳定（星号不会被当成新的加粗）
        val restored = InlineMarkup.materialize(parsed.visibleText, parsed.spans)
        assertEquals(parsed.visibleText, InlineMarkup.parseSpans(restored).visibleText)
    }

    @Test
    fun `内容自带两个星号时物化会丢格式但保住文字`() {
        // 直接构造一个覆盖 `a**b` 的加粗 span（编辑路径可能造出来）
        val visible = "a**b"
        val spans = listOf(InlineSpan(EmphasisTone.KEY, InlineBrush.BOLD, 0, visible.length))
        val stored = InlineMarkup.materialize(visible, spans)
        assertEquals("a**b", stored)
        assertEquals("a**b", InlineMarkup.stripMarkup(stored))
    }

    @Test
    fun `加粗 span 走同一套编辑代数（在末尾插入即扩展）`() {
        val spans = listOf(InlineSpan(EmphasisTone.KEY, InlineBrush.BOLD, 0, 2))
        val next = applyTypedText("重点", spans, "重点ABC", 5)
        assertEquals(5, next.spans.single().end)
    }

    @Test
    fun `加粗与语义标记可以相邻而不互相覆盖`() {
        val parsed = InlineMarkup.parseSpans("**粗**{{k:要点}}")
        assertEquals("粗要点", parsed.visibleText)
        assertEquals(2, parsed.spans.size)
        assertEquals(0, parsed.spans[0].start)
        assertEquals(1, parsed.spans[0].end)
        assertEquals(1, parsed.spans[1].start)
        assertEquals(3, parsed.spans[1].end)
    }

    @Test
    fun `光标停在已标记文字末尾时，新起的标记不会被旧标记吃掉`() {
        // 「标记中」：光标位于 "要点" 之后（offset 2），用户点了加粗，然后打字
        val existing = InlineSpan(EmphasisTone.KEY, InlineBrush.HIGHLIGHT, 0, 2)
        val pending = InlineSpan(EmphasisTone.KEY, InlineBrush.BOLD, 2, 2)
        val next = applyTypedText(
            previousText = "要点",
            spans = listOf(existing, pending),
            newText = "要点BOLD",
            newCaret = 6
        )
        assertEquals(2, next.spans.size)
        assertEquals(InlineBrush.HIGHLIGHT, next.spans[0].brush)
        assertEquals(0, next.spans[0].start)
        assertEquals(2, next.spans[0].end)
        assertEquals(InlineBrush.BOLD, next.spans[1].brush)
        assertEquals(2, next.spans[1].start)
        assertEquals(6, next.spans[1].end)
        assertEquals("{{k:要点}}**BOLD**", InlineMarkup.materialize(next.text, next.spans))
    }

    @Test
    fun `没有标记中就绪时，末尾输入仍然扩展旧标记（规则 2 不变）`() {
        val existing = InlineSpan(EmphasisTone.KEY, InlineBrush.HIGHLIGHT, 0, 2)
        val next = applyTypedText("要点", listOf(existing), "要点BOLD", 6)
        assertEquals(1, next.spans.size)
        assertEquals(6, next.spans.single().end)
    }
}
