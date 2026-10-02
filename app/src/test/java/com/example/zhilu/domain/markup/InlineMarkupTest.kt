package com.example.zhilu.domain.markup

import com.example.zhilu.domain.model.EmphasisTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内联语法的解析 / 物化 / 剥离。
 *
 * 覆盖设计文档 §3.6.1 的语法定义、§3.6.2 的容错规则、§12 的"不会被误解析"用例。
 */
class InlineMarkupTest {

    private fun span(
        tone: EmphasisTone,
        start: Int,
        end: Int,
        brush: InlineBrush = InlineBrush.HIGHLIGHT
    ) = InlineSpan(tone, brush, start, end)

    // ---- 解析 ----

    @Test
    fun `解析角色前缀并剥离标记`() {
        val parsed = InlineMarkup.parseSpans("{{k:要点}}")
        assertEquals("要点", parsed.visibleText)
        assertEquals(listOf(span(EmphasisTone.KEY, 0, 2)), parsed.spans)
    }

    @Test
    fun `解析单字标记`() {
        val parsed = InlineMarkup.parseSpans("标准形中{{i:正}}平方项")
        assertEquals("标准形中正平方项", parsed.visibleText)
        assertEquals(listOf(span(EmphasisTone.IDEA, 4, 5)), parsed.spans)
    }

    @Test
    fun `四种角色都能解析`() {
        val parsed = InlineMarkup.parseSpans("{{k:a}}{{i:b}}{{w:c}}{{t:d}}")
        assertEquals("abcd", parsed.visibleText)
        assertEquals(
            listOf(
                span(EmphasisTone.KEY, 0, 1),
                span(EmphasisTone.IDEA, 1, 2),
                span(EmphasisTone.WARN, 2, 3),
                span(EmphasisTone.TODO, 3, 4)
            ),
            parsed.spans
        )
    }

    @Test
    fun `三种笔触都能解析`() {
        val parsed = InlineMarkup.parseSpans("{{k:a}}{{k-c:b}}{{k-u:c}}")
        assertEquals("abc", parsed.visibleText)
        assertEquals(
            listOf(
                span(EmphasisTone.KEY, 0, 1, InlineBrush.HIGHLIGHT),
                span(EmphasisTone.KEY, 1, 2, InlineBrush.COLOR),
                span(EmphasisTone.KEY, 2, 3, InlineBrush.UNDERLINE)
            ),
            parsed.spans
        )
    }

    @Test
    fun `转义的字面量花括号不会被当成标记`() {
        val parsed = InlineMarkup.parseSpans("\\{{k:a}}")
        assertEquals("{{k:a}}", parsed.visibleText)
        assertTrue(parsed.spans.isEmpty())
    }

    // ---- 容错：一律原样显示，不吞字符 ----

    @Test
    fun `角色非法时原样显示`() {
        assertEquals("{{x:文字}}", InlineMarkup.stripMarkup("{{x:文字}}"))
    }

    @Test
    fun `缺少角色前缀时原样显示`() {
        assertEquals("{{文字}}", InlineMarkup.stripMarkup("{{文字}}"))
    }

    @Test
    fun `内容为空时原样显示`() {
        assertEquals("{{k:}}", InlineMarkup.stripMarkup("{{k:}}"))
    }

    @Test
    fun `未闭合时原样显示`() {
        assertEquals("{{k:未闭合", InlineMarkup.stripMarkup("{{k:未闭合"))
    }

    @Test
    fun `孤立闭合括号原样显示`() {
        assertEquals("文字}}", InlineMarkup.stripMarkup("文字}}"))
    }

    @Test
    fun `数学里的嵌套数组不会被误解析`() {
        val text = "反例 [[1, 2], [2, 1]] 的特征值为 3 与 −1。"
        assertEquals(text, InlineMarkup.stripMarkup(text))
    }

    @Test
    fun `等号与双等号不会被误解析`() {
        assertEquals("A = Aᵀ", InlineMarkup.stripMarkup("A = Aᵀ"))
        assertEquals("if (a == b)", InlineMarkup.stripMarkup("if (a == b)"))
    }

    @Test
    fun `数字开头的双花括号原样显示`() {
        assertEquals("{{1, 2}}", InlineMarkup.stripMarkup("{{1, 2}}"))
    }

    @Test
    fun `内容不含换行的标记不跨行`() {
        val text = "{{k:abc\ndef}}"
        assertEquals(text, InlineMarkup.stripMarkup(text))
    }

    // ---- 物化与往返 ----

    @Test
    fun `物化零长标记会被丢弃`() {
        val text = "正文"
        assertEquals(text, InlineMarkup.materialize(text, listOf(span(EmphasisTone.KEY, 1, 1))))
    }

    @Test
    fun `往返恒等 —— 合法文本`() {
        val samples = listOf(
            "",
            "纯文本，没有任何标记。",
            "{{k:要点}}",
            "标准形中{{i:正}}平方项与{{i:负}}平方项",
            "{{w-u:别超过 20 分钟}}",
            "{{t:下周一前出预算}}；{{k:Q3 主攻海外}}",
            "字面量 \\{{ 与 \\}} 也要还原",
            "反例 [[1, 2], [2, 1]] 与 A = Aᵀ",
            "{{k:跨 }} 不成立，所以内容里没有闭合括号"
        )
        for (sample in samples) {
            val parsed = InlineMarkup.parseSpans(sample)
            assertEquals("往返不恒等：$sample", sample, InlineMarkup.materialize(parsed.visibleText, parsed.spans))
        }
    }

    @Test
    fun `随机往返 —— 性质测试`() {
        val alphabet = listOf(
            "正", "定", "矩", "阵", " ", "\n", "a", "1", "=", "{{", "}}", "\\{{", "\\}}",
            "{{k:", "}}", "{{i:", "{{k-u:", "[[", "]]"
        )
        val random = kotlin.random.Random(20261002)
        repeat(400) {
            val raw = buildString {
                repeat(random.nextInt(0, 14)) { append(alphabet[random.nextInt(alphabet.size)]) }
            }
            val parsed = InlineMarkup.parseSpans(raw)
            // 可见文本不受语法影响：剥离后的内容必须与解析结果一致
            assertEquals(parsed.visibleText, InlineMarkup.stripMarkup(raw))
            // 物化后的文本再解析，必须得到同样的可见文本与标记表（幂等）
            val rematerialized = InlineMarkup.materialize(parsed.visibleText, parsed.spans)
            val reparsed = InlineMarkup.parseSpans(rematerialized)
            assertEquals("可见文本在二次解析后变化：$raw", parsed.visibleText, reparsed.visibleText)
            assertEquals("标记表在二次解析后变化：$raw", parsed.spans, reparsed.spans)
        }
    }

    @Test
    fun `剥离子串时不影响其它内容`() {
        val text = "{{k:唯一确定}}，与所用的可逆线性变换无关。"
        assertEquals("唯一确定，与所用的可逆线性变换无关。", InlineMarkup.stripMarkup(text))
    }
}
