package com.example.zhilu.domain.markup

import com.example.zhilu.domain.model.EmphasisTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 行内原子（P1：公式）。
 *
 * 坐标必须与 `InlineSpan` 一致（块内可见文本偏移）—— 这样原子才能自动获得
 * 与标记同等的编辑行为，`InlineSpanAdjuster` 一行都不用改。
 */
class InlineNodesTest {

    @Test
    fun `解析行内公式`() {
        val text = "见 \$a+b\$ 与 \$x^2\$ 说明"
        val nodes = InlineNodes.of(text)
        assertEquals(2, nodes.size)
        assertEquals(InlineKind.MATH, nodes[0].kind)
        assertEquals("\$a+b\$", text.substring(nodes[0].start, nodes[0].end))
        assertEquals("\$x^2\$", text.substring(nodes[1].start, nodes[1].end))
    }

    @Test
    fun `块级公式不会被当成两个行内`() {
        val text = "前\$\$x+y\$\$后"
        val nodes = InlineNodes.of(text)
        // 块级公式由 MathSpans 吃掉；这里只要不产出"半个公式"就行
        assertTrue(nodes.none { it.length < 3 })
    }

    @Test
    fun `没有美元符号就没有原子`() {
        assertTrue(InlineNodes.of("普通文本 {{k:要点}}").isEmpty())
        assertTrue(InlineNodes.of("价格 5\$ 一件").isEmpty())
    }

    @Test
    fun `原子内容去掉定界符`() {
        val text = "见 \$W_{\\text{发}} \\le 2^{n-1}\$ 说明"
        val node = InlineNodes.of(text).single()
        assertEquals("W_{\\text{发}} \\le 2^{n-1}", node.contentOf(text))
    }

    @Test
    fun `光标在原子内或两端都算命中`() {
        val node = InlineNode(InlineKind.MATH, 2, 7)
        assertTrue(node.contains(2))
        assertTrue(node.contains(4))
        assertTrue(node.contains(7))
        assertTrue(!node.contains(8))
        assertNull(InlineNodes.containing(listOf(node), 1))
    }

    @Test
    fun `光标所在的原子不隐藏其余隐藏`() {
        val text = "见 \$a+b\$ 与 \$x^2\$ 说明"
        val nodes = InlineNodes.of(text)
        val active = nodes[0]
        val hidden = InlineNodes.hiddenMath(nodes, active.start + 1)
        assertEquals(1, hidden.size)
        assertEquals(nodes[1].start, hidden[0].start)

        // 光标不在任何原子内 → 全部隐藏（渲染成最终形态）
        assertEquals(2, InlineNodes.hiddenMath(nodes, 0).size)
    }

    // ---- P2：行内代码 ----

    @Test
    fun `解析行内代码`() {        val text = "调用 `code()` 即可"
        val nodes = InlineNodes.of(text)
        assertEquals(1, nodes.size)
        assertEquals(InlineKind.CODE, nodes[0].kind)
        assertEquals("`code()`", text.substring(nodes[0].start, nodes[0].end))
    }

    @Test
    fun `公式与行内代码混在一起时按起点排序`() {
        val text = "先 \$a\$ 再 `b` 后 \$c\$"
        val kinds = InlineNodes.of(text).map { it.kind }
        assertEquals(listOf(InlineKind.MATH, InlineKind.CODE, InlineKind.MATH), kinds)
    }

    @Test
    fun `行内代码不参与隐藏`() {
        val text = "看 \$a\$ 与 `b`"
        val nodes = InlineNodes.of(text)
        val hidden = InlineNodes.hiddenMath(nodes, null)
        assertEquals(1, hidden.size)
        assertEquals(InlineKind.MATH, hidden[0].kind)
    }

    @Test
    fun `空反引号与跨行反引号都不算原子`() {
        // 相邻两个反引号：内容为空 → 不匹配
        assertTrue(InlineNodes.of("空 \u0060\u0060 而已").isEmpty())
        // 反引号跨行 → 不匹配（行内代码不跨行）
        assertTrue(InlineNodes.of("跨行 \u0060a\nb\u0060 结束").isEmpty())
    }

    @Test
    fun `选区也会吸附到整条行内代码`() {
        val text = "调用 \u0060code()\u0060 即可"
        val start = text.indexOf('\u0060')
        val end = text.lastIndexOf('\u0060') + 1
        // 只框到源码中间一小段 → 吸附成整条（含两侧定界符）
        val (from, to) = InlineNodes.snapOutside(text, start + 1, start + 3)
        assertEquals(start, from)
        assertEquals(end, to)
    }

    @Test
    fun `整条行内代码被标记包住可以原样读回`() {
        // 行内代码里没有花括号，所以"整条标记"本来就是合法存储形态；
        // 这条测试锁住的是"吸附后能往返"，避免以后改动把代码原子写坏。
        val text = "调用 `code()` 即可"
        val node = InlineNodes.of(text).single()
        val stored = InlineMarkup.materialize(
            text,
            listOf(InlineSpan(EmphasisTone.KEY, InlineBrush.HIGHLIGHT, node.start, node.end))
        )
        assertEquals("调用 {{k:`code()`}} 即可", stored)
        val parsed = InlineMarkup.parseSpans(stored)
        assertEquals(text, parsed.visibleText)
        assertEquals(node.start, parsed.spans.single().start)
        assertEquals(node.end, parsed.spans.single().end)
    }

    // ---- P4：行内链接（用户已确认"自动识别"）----

    @Test
    fun `自动识别行内链接`() {
        val text = "参考 [官方文档](https://example.com/a) 与其它"
        val node = InlineNodes.of(text).single()
        assertEquals(InlineKind.LINK, node.kind)
        assertEquals("[官方文档](https://example.com/a)", text.substring(node.start, node.end))
    }

    @Test
    fun `三类原子混排时按起点排序`() {
        val text = "先 \$a\$ 再 `b` 后 [c](d)"
        assertEquals(
            listOf(InlineKind.MATH, InlineKind.CODE, InlineKind.LINK),
            InlineNodes.of(text).map { it.kind }
        )
    }

    @Test
    fun `不完整的链接写法不算原子`() {
        // 只有方括号、或只有圆括号，都不是链接（避免把正文里的普通括号当链接）
        assertTrue(InlineNodes.of("见 [注释] 说明").isEmpty())
        assertTrue(InlineNodes.of("见 (a) 说明").isEmpty())
        assertTrue(InlineNodes.of("跨行 [文字]\n(url)").isEmpty())
    }

    @Test
    fun `选区吸附到整条链接`() {
        val text = "参考 [官方文档](https://example.com/a) 与其它"
        val node = InlineNodes.of(text).single()
        val (from, to) = InlineNodes.snapOutside(text, node.start + 2, node.start + 4)
        assertEquals(node.start, from)
        assertEquals(node.end, to)
    }

    @Test
    fun `整条链接被标记包住可以原样读回`() {
        // 链接里没有花括号，所以"整条标记"本来就合法；这条锁住吸附后仍能往返
        val text = "参考 [文档](https://a.b) 即可"
        val node = InlineNodes.of(text).single()
        val stored = InlineMarkup.materialize(
            text,
            listOf(InlineSpan(EmphasisTone.KEY, InlineBrush.HIGHLIGHT, node.start, node.end))
        )
        assertEquals("参考 {{k:[文档](https://a.b)}} 即可", stored)
        val parsed = InlineMarkup.parseSpans(stored)
        assertEquals(text, parsed.visibleText)
        assertEquals(node.start, parsed.spans.single().start)
        assertEquals(node.end, parsed.spans.single().end)
    }

    @Test
    fun `原子随文字增删自动伸缩`() {        // 原子是"现算"的，不落任何状态：在公式前插字，区间整体右移且内容不变
        val before = "见 \$a+b\$ 说明"
        val after = "又见 \$a+b\$ 说明"
        val nodeBefore = InlineNodes.of(before).single()
        val nodeAfter = InlineNodes.of(after).single()
        assertEquals(1, nodeAfter.start - nodeBefore.start)
        assertEquals(nodeBefore.contentOf(before), nodeAfter.contentOf(after))
    }
}
