package com.example.zhilu.domain.markup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 原子可见性：**哪些公式该隐藏源码、由覆盖层补画**。
 *
 * 这一条直接决定"编辑态还看不看得见公式"：漏掉一个，那条公式当场从屏幕上消失
 * （不是变小、不是错位，是整条没了），而几何诊断一切正常 —— 因为覆盖层根本没收到它。
 * 所以边界必须逐个钉死。
 */
class InlineNodesVisibilityTest {

    /** 两行各一条公式：`第一行… $a+b$ 结束\n第二行… $c^2$ 结束` */
    private val twoLines = "第一行 \$a+b\$ 结束\n第二行 \$c^2\$ 结束"

    @Test
    fun `光标不在任何公式里时_两条公式都该画`() {
        val nodes = InlineNodes.of(twoLines)
        assertEquals(2, nodes.size)
        // 失焦（activeOffset = null）
        assertEquals(2, InlineNodes.hiddenMath(nodes, null).size)
    }

    @Test
    fun `光标在公式之后_该公式仍然要画`() {
        val nodes = InlineNodes.of(twoLines)
        val afterFirst = nodes[0].end + 1
        // 光标在第一行公式**之后**（不是里面）：两条都该隐藏源码、都该画图
        assertEquals(2, InlineNodes.hiddenMath(nodes, afterFirst).size)
    }

    @Test
    fun `光标在公式内部时_只有那一条恢复源码`() {
        val nodes = InlineNodes.of(twoLines)
        val insideFirst = nodes[0].start + 1
        val hidden = InlineNodes.hiddenMath(nodes, insideFirst)
        assertEquals(1, hidden.size)
        assertEquals(nodes[1], hidden.first())
    }

    @Test
    fun `在两条公式之间插入换行_两条都还在`() {
        // 复现"光标在行间插入"：在第一行末尾（公式之后）插入 \n
        val inserted = "第一行 \$a+b\$ \n结束\n第二行 \$c^2\$ 结束"
        val nodes = InlineNodes.of(inserted)
        assertEquals("插入换行不该吃掉任何一条公式", 2, nodes.size)
        // 光标落在新换行之后（第二行行首）
        val caretAfterNewline = inserted.indexOf('\n') + 1
        assertEquals(2, InlineNodes.hiddenMath(nodes, caretAfterNewline).size)
    }

    @Test
    fun `公式自身含换行时不再是公式_源码如实露出`() {
        // 光标停在公式中间按回车 → `$a+\nb$`。语法上是**不合法**的（公式不跨行），
        // 于是它退化成普通文字：既没有图，源码也不再透明 —— 这是"公式消失"的真相，
        // 是**语法约束**下的预期行为，不是渲染漏画。
        val broken = "第一行 \$a+\nb\$ 结束"
        val nodes = InlineNodes.of(broken)
        assertTrue("跨行的 `$…$` 不该被当成公式", nodes.none { it.kind == InlineKind.MATH })
    }

    @Test
    fun `块级公式不会被拆成一条半的行内公式`() {
        // `$$E=mc^2$$` 里，行内规则本来会匹配到 `$E=mc^2$`（第二个 `$` 到倒数第二个）——
        // 那是一条**假的**行内公式，源码里还残留一个 `$`。块级优先 + 相交剔除之后只剩一条。
        val nodes = InlineNodes.of("\$\$E=mc^2\$\$")
        assertEquals(1, nodes.size)
        assertEquals(InlineKind.MATH, nodes.first().kind)
        assertEquals("E=mc^2", nodes.first().contentOf("\$\$E=mc^2\$\$"))
    }

    @Test
    fun `块级与行内混排时两条都认出来`() {
        val text = "块级：\n\$\$E=mc^2\$\$\n行内：\$a\$"
        val nodes = InlineNodes.of(text)
        assertEquals(2, nodes.size)
        assertEquals(listOf("E=mc^2", "a"), nodes.map { it.contentOf(text) })
    }

    @Test
    fun `空文本与无原子不抛异常`() {
        assertEquals(0, InlineNodes.of("").size)
        assertEquals(0, InlineNodes.hiddenMath(emptyList(), 0).size)
        assertEquals(0, InlineNodes.hiddenMath(InlineNodes.of("纯文字"), 2).size)
    }

    @Test
    fun `行内代码不参与隐藏`() {
        val text = "公式 \$a\$ 与代码 `x` 混排"
        val nodes = InlineNodes.of(text)
        val hidden = InlineNodes.hiddenMath(nodes, null)
        assertEquals(1, hidden.size)
        assertEquals(InlineKind.MATH, hidden.first().kind)
    }
}
