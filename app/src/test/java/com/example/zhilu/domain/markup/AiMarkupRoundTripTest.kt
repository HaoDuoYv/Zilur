package com.example.zhilu.domain.markup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 写入路径的往返安全（`AiToolExecutor` → [InlineMarkupNormalizer.normalize] → 落库）。
 *
 * 为什么单独钉这一组：AI 是**文本直接落库**的入口之一，它写进去的正文会经
 * `normalize` → `materialize`，而 `materialize` 里有一道
 * `MathSpans.ranges` + `canCover` 的校验（标记必须完整包住公式，切进公式内部一律不合法）。
 * 也就是说 **`MathSpans.ranges` 的判据会直接影响 AI 写入的成败**：
 * 判多了会凭空多出一条"公式"，让本该合法的标记被判成"切进公式"而丢掉。
 *
 * 这里锁的就是"改了 `ranges` 之后，AI 的两类典型写入仍然一字不差"：
 * ① 带行内公式的正文（AI 最常写的东西）；
 * ② 带语义标记的正文（`set_block_emphasis` 与 `add_blocks` 都会产生）。
 * 验收基准与 `InlineMarkupNormalizerTest` 一致：**最坏情况是标记掉了、文字还在**。
 */
class AiMarkupRoundTripTest {

    private fun normalize(text: String) = InlineMarkupNormalizer.normalize(text)

    /**
     * 落库形态 → **可见文本**。
     *
     * 用 [InlineMarkup.stripMarkup] 而不是 `parseSpans`：正文里的花括号会被转义成 `\{`/`\}`，
     * 那是**存储形态**的编码，不是用户看到的文字（`stripMarkup` 才是各出口实际用的那道）。
     */
    private fun visibleOf(stored: String) = InlineMarkup.stripMarkup(stored)

    /** 把存储形态的转义（`\{` `\}`）还原成用户看到的字符，便于逐字比对。 */
    private fun unescaped(text: String) = text.replace("\\{", "{").replace("\\}", "}")

    // ── 带公式的正文 ────────────────────────────────────────────────────

    @Test
    fun `行内公式原样保留`() {
        val written = "GBN：代入 \$W_{\\text{发}}=W_{\\text{收}}=W\$ → \$2W \\le 2^n\$"
        val stored = normalize(written)
        assertEquals(written, stored)
        assertEquals(written, visibleOf(stored))
    }

    @Test
    fun `块级公式原样保留`() {
        val written = "推导如下：\n\$\$E=mc^2\$\$\n上式即结论。"
        val stored = normalize(written)
        assertEquals(written, stored)
        assertEquals(written, visibleOf(stored))
    }

    @Test
    fun `AI 按工具说明写的裸 LaTeX 源码不被改写`() {
        // add_blocks 的工具描述要求 latex 块"只写裸 LaTeX 源码（不含 $$ 包裹）"。
        // 源码里的花括号会被转义成 `\{`/`\}`（存储形态的编码），但**读回可见文本时必须还原**
        val written = "W_{\\text{发}} \\le 2^n - 1"
        val stored = normalize(written)
        assertEquals("可见文本必须一字不差", written, visibleOf(stored))
        assertEquals("规整必须幂等", stored, normalize(stored))
    }

    // ── 带语义标记的正文（set_block_emphasis / add_blocks）──────────────

    @Test
    fun `语义标记完好保留`() {
        val written = "标准形中{{i:正}}平方项的个数是{{k:惯性指数}}"
        assertEquals(written, normalize(written))
    }

    @Test
    fun `标记整条包住公式时合法`() {
        // 存储形态允许的唯一"公式上色"写法（设计文档 §14.5 的例外）
        val written = "结论：{{k:\$W \\le 2^{n-1}\$}} 成立"
        val stored = normalize(written)
        assertEquals("整条公式被标记包住必须能存下来", written, stored)
        assertEquals("结论：\$W \\le 2^{n-1}\$ 成立", visibleOf(stored))
    }

    @Test
    fun `标记切进公式内部时丢标记但正文一字不少`() {
        // 这种写法不合法（会把 `{{k:…}}` 织进 LaTeX 花括号里），normalize 必须降级成"只有文字"。
        //
        // 这一条**记录的是当前行为**：标记体外那个多余的 `}` 会被当成分隔符吃掉
        // （`预期 = 输入 - {{k: - 一个 }`），而裸 `}` 在存储形态里又会被转义成 `\}`。
        // 断言按"转义还原后"逐字比对 —— 编码（`\}`）不算丢字，少一个 `}` 才算。
        val written = "代入 {{k:W_{\\text{发}}} 的值"
        val stored = normalize(written)
        assertEquals(
            "最坏情况只能是标记掉了、文字还在",
            "代入 W_{\\text{发} 的值",
            unescaped(visibleOf(stored))
        )
        assertTrue("非法标记不该留下语法字符：$stored", !stored.contains("{{k:"))
        assertEquals("规整必须幂等", stored, normalize(stored))
    }

    // ── 公式与块级公式混排（`ranges` 判据改动直接命中的场景）────────────

    @Test
    fun `块级公式与行内公式混排时标记仍然合法`() {
        val written = "块级：\n\$\$E=mc^2\$\$\n行内：{{i:\$a+b\$}} 结束"
        val stored = normalize(written)
        assertEquals(written, stored)
        assertEquals(
            "块级：\n\$\$E=mc^2\$\$\n行内：\$a+b\$ 结束",
            visibleOf(stored)
        )
    }

    @Test
    fun `写坏一半的标记不会吃掉公式`() {
        // AI 偶尔会漏掉右侧花括号：语法降级，但公式与文字都必须在
        val written = "结论：{{k:\$W \\le 2^{n-1}\$ 成立"
        val stored = normalize(written)
        assertEquals("结论：\$W \\le 2^{n-1}\$ 成立", visibleOf(stored))
    }

    @Test
    fun `规整幂等 —— 二次规整不再变化`() {
        val samples = listOf(
            "行内 \$a+b\$ 与 \$c^2\$",
            "块级 \$\$E=mc^2\$\$ 行内 \$a\$",
            "{{k:\$W \\le 2^{n-1}\$}}",
            "标准形中{{i:正}}平方项",
            "代入 {{k:W_{\\text{发}}} 的值",
            "结论：{{k:\$W \\le 2^{n-1}\$ 成立"
        )
        for (sample in samples) {
            val once = normalize(sample)
            assertEquals("二次规整不稳定：$sample", once, normalize(once))
        }
    }
}
