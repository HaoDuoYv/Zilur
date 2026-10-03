package com.example.zhilu.domain.markup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 行内公式 ↔ 公式块。
 *
 * 落库格式不变这条要一起锁住：行内是 `$…$`，块里是裸 LaTeX 源码。
 */
class InlineFormulaConversionTest {

    private fun mathNode(text: String): InlineNode =
        InlineNodes.of(text).first { it.kind == InlineKind.MATH }

    @Test
    fun `把句子中间的公式提升为块`() {
        val text = "调用 \$a+b\$ 结束"
        val (rest, source) = InlineFormulaConversion.promote(text, mathNode(text))!!
        // 摘掉公式，并且不留双空格
        assertEquals("调用 结束", rest)
        assertEquals("a+b", source)
    }

    @Test
    fun `公式在句首句尾也能提升`() {
        val head = "\$x^2\$ 的导数"
        assertEquals("的导数", InlineFormulaConversion.promote(head, mathNode(head))!!.first)

        val tail = "结果等于 \$x^2\$"
        assertEquals("结果等于", InlineFormulaConversion.promote(tail, mathNode(tail))!!.first)
    }

    @Test
    fun `整块正文就是一条公式时摘完为空`() {
        val text = "\$E = mc^2\$"
        val (rest, source) = InlineFormulaConversion.promote(text, mathNode(text))!!
        assertEquals("", rest)
        assertEquals("E = mc^2", source)
    }

    @Test
    fun `带花括号的公式源码原样搬走`() {
        val text = "见 \$W_{\\text{发}} \\le 2^{n-1}\$ 说明"
        val (rest, source) = InlineFormulaConversion.promote(text, mathNode(text))!!
        assertEquals("见 说明", rest)
        assertEquals("W_{\\text{发}} \\le 2^{n-1}", source)
    }

    @Test
    fun `行内代码不会被提升`() {
        val text = "调用 `code()` 即可"
        val code = InlineNodes.of(text).first { it.kind == InlineKind.CODE }
        assertNull(InlineFormulaConversion.promote(text, code))
    }

    @Test
    fun `降级把多行公式压成单行`() {
        assertEquals(
            "\$a \\\\ b\$",
            InlineFormulaConversion.demoteToInline("a \\\\\n  b")
        )
        assertEquals("\$\\frac{a}{b}\$", InlineFormulaConversion.demoteToInline("  \\frac{a}{b}  "))
    }

    @Test
    fun `空公式没有降级形态`() {
        assertNull(InlineFormulaConversion.demoteToInline("   "))
        assertNull(InlineFormulaConversion.demoteToInline("\n\n"))
    }

    @Test
    fun `提升再降级能回到可解析的行内形态`() {
        val text = "见 \$W_{\\text{发}} \\le 2^{n-1}\$ 说明"
        val (rest, source) = InlineFormulaConversion.promote(text, mathNode(text))!!
        val inline = InlineFormulaConversion.demoteToInline(source)!!
        val restored = rest.replaceFirst("说明", "$inline 说明")
        assertEquals(text, restored)
        assertEquals(1, InlineNodes.of(restored).count { it.kind == InlineKind.MATH })
    }
}
