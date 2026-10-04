package com.example.zhilu.ui.note.blocks

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.example.zhilu.domain.markup.InlineKind
import com.example.zhilu.domain.markup.InlineNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 公式源码的**透明规则**：除了光标所在的那一条，其余全部透明。
 *
 * 这条曾经漏掉一半：覆盖层（`InlineNodes.hiddenMath`）已经不再画光标所在的公式，
 * 而这里仍然把它涂成透明 —— 于是那条公式**既没有图、也看不见源码**，
 * 屏幕上只剩一块选区底色，表现就是"点进公式，公式消失了"（用户截图报过）。
 *
 * 两个判断必须同源，所以这里直接钉住"活动原子必须不是透明色"。
 */
class AtomStyleTransformationTest {

    private val formulaA = "调用 \$a+b\$ 结束"
    private val formulaB = "第一行 \$a+b\$ 结束\n第二行 \$c^2\$ 结束"

    private fun mathNodes(text: String) =
        com.example.zhilu.domain.markup.InlineNodes.of(text).filter { it.kind == InlineKind.MATH }

    /**
     * 取覆盖 [offset] 那个字符的 span 颜色（后加的样式优先，取最后一个命中的）。
     *
     * 注意 `Color.Unspecified` 是**合法值**：活动公式走的是"等宽 + 不设色"（跟随正文），
     * 只有"没被任何透明 span 命中"才算可见 —— 判定条件是 `!= Transparent`，不是 `!= Unspecified`。
     */
    private fun colorAt(transformed: AnnotatedString, offset: Int): Color? =
        transformed.spanStyles
            .filter { it.start <= offset && offset < it.end }
            .lastOrNull { it.item.color != Color.Unspecified }
            ?.item
            ?.color
            ?: Color.Unspecified

    @Test
    fun `光标不在任何公式里时_源码全部透明`() {
        val nodes = mathNodes(formulaA)
        val transformed = AtomStyleTransformation(nodes = nodes, activeAtomOffset = null)
            .filter(AnnotatedString(formulaA))
            .text

        val node = nodes.single()
        assertEquals(Color.Transparent, colorAt(transformed, node.start + 1))
    }

    @Test
    fun `光标在公式内时_那一条源码不透明`() {
        val nodes = mathNodes(formulaA)
        val node = nodes.single()
        val caretInside = node.start + 2

        val transformed = AtomStyleTransformation(nodes = nodes, activeAtomOffset = caretInside)
            .filter(AnnotatedString(formulaA))
            .text

        assertTrue(
            "光标所在公式的源码必须可见（否则公式既没图也没源码）：" +
                "实际 color=${colorAt(transformed, caretInside)}",
            colorAt(transformed, caretInside) != Color.Transparent
        )
    }

    @Test
    fun `两行时_光标只影响它所在的那一条`() {
        val nodes = mathNodes(formulaB)
        assertEquals(2, nodes.size)
        val caretInFirst = nodes[0].start + 1
        val transformed = AtomStyleTransformation(nodes = nodes, activeAtomOffset = caretInFirst)
            .filter(AnnotatedString(formulaB))
            .text

        assertTrue("第一条应恢复源码", colorAt(transformed, caretInFirst) != Color.Transparent)
        assertEquals("第二条仍应透明（由覆盖层补画）", Color.Transparent, colorAt(transformed, nodes[1].start + 1))
    }

    @Test
    fun `光标紧贴公式两端也算在里面`() {
        val nodes = mathNodes(formulaA)
        val node = nodes.single()
        // `contains` 两端闭：公式末尾继续打字也应当看得见源码
        for (offset in listOf(node.start, node.end)) {
            val transformed = AtomStyleTransformation(nodes = nodes, activeAtomOffset = offset)
                .filter(AnnotatedString(formulaA))
                .text
            assertTrue(
                "offset=$offset 时源码应可见，实际 ${colorAt(transformed, node.start + 1)}",
                colorAt(transformed, node.start + 1) != Color.Transparent
            )
        }
    }

    @Test
    fun `行内代码不参与透明_照常可见`() {
        val text = "代码 `x` 与公式 \$a\$"
        val nodes = com.example.zhilu.domain.markup.InlineNodes.of(text)
        val transformed = AtomStyleTransformation(nodes = nodes, activeAtomOffset = null)
            .filter(AnnotatedString(text))
            .text

        val code = nodes.first { it.kind == InlineKind.CODE }
        // 代码段没有任何"透明"样式（它的样式是等宽 + 底色，颜色可能 Unspecified）
        val transparentSpans = transformed.spanStyles.filter { it.item.color == Color.Transparent }
        assertTrue(
            "代码段不该被涂成透明",
            transparentSpans.none { it.start <= code.start && code.start < it.end }
        )
    }

    @Test
    fun `变换不改文本长度_偏移映射恒等`() {
        val nodes = mathNodes(formulaB)
        val output = AtomStyleTransformation(nodes = nodes, activeAtomOffset = 3)
            .filter(AnnotatedString(formulaB))
        assertEquals(formulaB, output.text.text)
        assertEquals(
            "偏移映射必须是恒等的",
            formulaB.length,
            output.text.text.length
        )
    }
}
