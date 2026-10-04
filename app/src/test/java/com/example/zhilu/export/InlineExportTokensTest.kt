package com.example.zhilu.export

import com.example.zhilu.domain.markup.InlineBrush
import com.example.zhilu.domain.model.EmphasisTone
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 行内 token 词法器。
 *
 * 它是 HTML / Markdown **两个导出器共用的唯一解析**（原先各写各的，于是"行内公式渲染成图片"
 * 只在其中一个上成立）。这里把分流规则逐条钉住。
 */
class InlineExportTokensTest {

    @Test
    fun `普通文字是一整段`() {
        assertEquals(
            listOf(InlineExportToken.Plain("就是一段普通文字")),
            inlineExportTokens("就是一段普通文字")
        )
    }

    @Test
    fun `行内公式剥掉定界符`() {
        val tokens = inlineExportTokens("序号空间 = \$2^n\$")
        assertEquals(
            listOf(
                InlineExportToken.Plain("序号空间 = "),
                InlineExportToken.Formula("2^n", display = false)
            ),
            tokens
        )
    }

    @Test
    fun `块级公式标记为 display`() {
        val tokens = inlineExportTokens("推导\n\$\$E=mc^2\$\$")
        assertEquals(
            listOf(
                InlineExportToken.Plain("推导\n"),
                InlineExportToken.Formula("E=mc^2", display = true)
            ),
            tokens
        )
    }

    @Test
    fun `块级必须先于行内匹配`() {
        // 反过来的话行内规则会从第一个 `$` 吃到最后一个 `$`，把中间文字吞进公式
        val tokens = inlineExportTokens("\$\$E=mc^2\$\$\n行内：\$a\$")
        val formulas = tokens.filterIsInstance<InlineExportToken.Formula>()
        assertEquals(2, formulas.size)
        assertEquals("E=mc^2", formulas[0].latex)
        assertEquals(true, formulas[0].display)
        assertEquals("a", formulas[1].latex)
        assertEquals(false, formulas[1].display)
    }

    @Test
    fun `标记段里的公式带上语义色`() {
        val tokens = inlineExportTokens("结论：{{k:\$W \\le 2^{n-1}\$}} 成立")
        val formula = tokens.filterIsInstance<InlineExportToken.Formula>().single()
        assertEquals("W \\le 2^{n-1}", formula.latex)
        assertEquals(com.example.zhilu.domain.model.EmphasisTone.KEY, formula.tone)
    }

    @Test
    fun `标记段里的普通文字仍是 Styled`() {
        // 这一条是抓 bug 抓出来的：第一版把 Marked 段里的普通文字吐成 Plain，
        // 于是 `{{i:正}}` 的强调信息在导出里整个消失（HTML 里再也看不到语义色）。
        val tokens = inlineExportTokens("标准形中{{i:正}}平方项")
        assertEquals(
            listOf(
                InlineExportToken.Plain("标准形中"),
                InlineExportToken.Styled("正", EmphasisTone.IDEA, InlineBrush.HIGHLIGHT),
                InlineExportToken.Plain("平方项")
            ),
            tokens
        )
    }

    @Test
    fun `标记段里夹着公式时，公式带走语义色、文字各自成段`() {
        val tokens = inlineExportTokens("结论：{{k:见 \$2^n\$ 处}}")
        assertEquals(
            listOf(
                InlineExportToken.Plain("结论："),
                InlineExportToken.Styled("见 ", EmphasisTone.KEY, InlineBrush.HIGHLIGHT),
                InlineExportToken.Formula("2^n", display = false, tone = EmphasisTone.KEY, brush = InlineBrush.HIGHLIGHT),
                InlineExportToken.Styled(" 处", EmphasisTone.KEY, InlineBrush.HIGHLIGHT)
            ),
            tokens
        )
    }

    @Test
    fun `行内代码与链接各归各的`() {
        val tokens = inlineExportTokens("用 `git rebase` 见 [文档](https://e.com/a)")
        assertEquals(
            listOf(
                InlineExportToken.Plain("用 "),
                InlineExportToken.Code("git rebase"),
                InlineExportToken.Plain(" 见 "),
                InlineExportToken.Link("文档", "https://e.com/a")
            ),
            tokens
        )
    }

    @Test
    fun `公式里的花括号与反斜杠不会被当成标记`() {
        val tokens = inlineExportTokens("\$W_{\\text{发}}=W_{\\text{收}}\$")
        val formula = tokens.filterIsInstance<InlineExportToken.Formula>().single()
        assertEquals("W_{\\text{发}}=W_{\\text{收}}", formula.latex)
    }
}
