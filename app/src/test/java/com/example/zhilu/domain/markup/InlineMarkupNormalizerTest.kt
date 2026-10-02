package com.example.zhilu.domain.markup

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 语法规整（设计文档 §3.11.3 的 6 条规则）。
 *
 * 唯一验收标准：**最坏情况是标记掉了、文字还在**。
 */
class InlineMarkupNormalizerTest {

    private fun normalize(text: String) = InlineMarkupNormalizer.normalize(text)

    @Test
    fun `合法文本原样保留`() {
        assertEquals("{{k:惯性定理}}", normalize("{{k:惯性定理}}"))
        assertEquals("标准形中{{i:正}}平方项", normalize("标准形中{{i:正}}平方项"))
    }

    @Test
    fun `未闭合时丢掉语法字符但保留文字`() {
        assertEquals("文字", normalize("{{k:文字"))
    }

    @Test
    fun `角色非法时丢掉语法字符但保留文字`() {
        assertEquals("文字", normalize("{{x:文字}}"))
    }

    @Test
    fun `内容为空时整段移除`() {
        assertEquals("", normalize("{{k:}}"))
        assertEquals("前后", normalize("前{{k:}}后"))
    }

    @Test
    fun `嵌套时内层优先外层降级为文字`() {
        assertEquals("a{{i:b}}c", normalize("{{k:a{{i:b}}c}}"))
    }

    @Test
    fun `跨行标记拆成逐行标记`() {
        assertEquals("{{k:abc}}\n{{k:def}}", normalize("{{k:abc\ndef}}"))
    }

    @Test
    fun `纯文本不被动过`() {
        val text = "反例 [[1, 2], [2, 1]] 的特征值为 3 与 −1；且 A = Aᵀ。"
        assertEquals(text, normalize(text))
    }

    @Test
    fun `孤立闭合括号被转义保留而不是吃掉`() {
        // 用户内容里的 } 一个都不能丢
        val result = normalize("文字}}")
        assertEquals("文字}}", InlineMarkup.stripMarkup(result))
    }

    @Test
    fun `数字开头的双花括号不会被当成标记`() {
        val result = normalize("{{1, 2}}")
        assertEquals("{{1, 2}}", InlineMarkup.stripMarkup(result))
    }

    @Test
    fun `规整后的文本再规整一次不变`() {
        val samples = listOf(
            "{{k:文字",
            "{{x:文字}}",
            "{{k:}}",
            "{{k:a{{i:b}}c}}",
            "{{k:abc\ndef}}",
            "文字}}",
            "{{1, 2}}",
            "{{k:合法}}{{i:也合法}}"
        )
        for (sample in samples) {
            val once = normalize(sample)
            assertEquals("二次规整不稳定：$sample", once, normalize(once))
        }
    }

    @Test
    fun `规整不会丢字 —— 可见文字一字不少`() {
        // 语法字符之外的正文必须全部保留
        assertEquals("只判顺序主子式不够", normalize("{{k:只判顺序主子式不够"))
        assertEquals("若存在可逆矩阵 C 使 B = CᵀAC", normalize("{{w:若存在可逆矩阵 C 使 B = CᵀAC"))
    }
}
