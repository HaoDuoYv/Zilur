package com.example.zhilu.ui.note.latex

import com.example.zhilu.domain.markup.InlineKind
import com.example.zhilu.domain.markup.InlineNodes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 编辑态与只读态**必须对同一段文本给出同一组公式**。
 *
 * 两条路径的解析器不同（编辑态 `InlineNodes` 走 `MathSpans`，只读态 `buildInlineLatexText`
 * 走自己的词法器），所以"某段文本在编辑态是公式、在只读态不是"这种不一致会直接表现成
 * **公式在某一侧凭空消失**。这里把最容易分歧的输入钉住。
 */
class InlineLatexParsingParityTest {

    private fun editPathFormulas(text: String): List<String> =
        InlineNodes.of(text)
            .filter { it.kind == InlineKind.MATH }
            .map { it.contentOf(text) }

    private fun readPathFormulas(text: String): List<String> =
        buildInlineLatexText(text, textColor = androidx.compose.ui.graphics.Color.Black).formulas

    private fun assertParity(text: String) {
        assertEquals(
            "编辑态与只读态解析出的公式必须一致：$text",
            editPathFormulas(text),
            readPathFormulas(text)
        )
    }

    @Test
    fun `单行行内公式两侧一致`() = assertParity("调用 \$a+b\$ 结束")

    @Test
    fun `多行多公式两侧一致`() = assertParity("第一行 \$a+b\$ 结束\n第二行 \$c^2\$ 结束")

    @Test
    fun `含 CJK 与下标的公式两侧一致`() = assertParity("代入 \$W_{\\text{发}} \\le 2^{n-1}\$ 即可")

    @Test
    fun `行间插入换行后两侧仍然一致`() =
        assertParity("第一行 \$a+b\$ \n结束\n第二行 \$c^2\$ 结束")

    @Test
    fun `块级公式与行内混排时两侧一致`() = assertParity("块级：\n\$\$E=mc^2\$\$\n行内：\$a\$")

    // ── 只读态自己的断言（不依赖编辑态，便于定位是哪一侧坏了）──────────────

    @Test
    fun `只读态识别单个行内公式`() {
        assertEquals(
            listOf("a+b"),
            readPathFormulas("调用 \$a+b\$ 结束")
        )
    }

    @Test
    fun `只读态不把块级公式的内部再切一刀`() {
        // `$$E=mc^2$$` 如果不优先匹配块级，`$…$` 会匹配到 `$E=mc^2$` 这一段
        assertEquals(
            listOf("E=mc^2"),
            readPathFormulas("\$\$E=mc^2\$\$")
        )
    }

    @Test
    fun `只读态不会把两条块级公式之间的正文吞进公式`() {
        // 这条是那个"文字消失"的 bug：老词法器会匹配第一个 `$` 到最后一个 `$` 之间的全部内容
        assertEquals(
            listOf("E=mc^2", "a"),
            readPathFormulas("块级：\n\$\$E=mc^2\$\$\n行内：\$a\$")
        )
    }
}
