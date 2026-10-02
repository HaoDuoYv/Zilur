package com.example.zhilu.domain.markup

import com.example.zhilu.domain.model.EmphasisTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * span 变换规则（设计文档 §3.11.2 的 8 条）。
 *
 * 这是"标记不进编辑缓冲"方案里唯一需要严谨定义的地方，
 * 所以每条规则都单独钉一个用例，边界插入尤其不能靠"看起来合理"。
 */
class InlineSpanAdjustTest {

    private val key = EmphasisTone.KEY

    private fun span(start: Int, end: Int, tone: EmphasisTone = key) =
        InlineSpan(tone, InlineBrush.HIGHLIGHT, start, end)

    // ---- 规则 1：内部插入 → 扩展 ----

    @Test
    fun `内部插入时扩展`() {
        val result = InlineSpanAdjuster.adjust(listOf(span(0, 3)), "abc", "aXbc")
        assertEquals(listOf(span(0, 4)), result)
    }

    // ---- 规则 2：末尾插入 → 扩展 ----

    @Test
    fun `末尾插入时扩展`() {
        val result = InlineSpanAdjuster.adjust(listOf(span(0, 3)), "abc", "abcd")
        assertEquals(listOf(span(0, 4)), result)
    }

    @Test
    fun `末尾连续输入持续扩展`() {
        var spans = listOf(span(0, 3))
        var text = "abc"
        for (char in "def") {
            val next = text + char
            spans = InlineSpanAdjuster.adjust(spans, text, next)
            text = next
        }
        assertEquals(listOf(span(0, 6)), spans)
    }

    // ---- 规则 3：起始插入 → 不扩展，内容右移 ----

    @Test
    fun `起始插入时不扩展且内容右移`() {
        val result = InlineSpanAdjuster.adjust(listOf(span(0, 3)), "abc", "Xabc")
        assertEquals(listOf(span(1, 4)), result)
    }

    // ---- 规则 4：删除相交 → 收缩 ----

    @Test
    fun `删除与标记相交时收缩`() {
        val result = InlineSpanAdjuster.adjust(listOf(span(0, 3)), "abc", "ac")
        assertEquals(listOf(span(0, 2)), result)
    }

    @Test
    fun `删除标记左半部分时起点右移`() {
        // "abcd" 的 [1,4) 被删掉 "bc" → 剩下 "ad"，标记覆盖 "d" → (1,2)
        val result = InlineSpanAdjuster.adjust(listOf(span(1, 4)), "abcd", "ad")
        assertEquals(listOf(span(1, 2)), result)
    }

    // ---- 规则 5：完全覆盖 → 删除 ----

    @Test
    fun `删除完全覆盖标记时标记消失`() {
        val result = InlineSpanAdjuster.adjust(listOf(span(0, 3)), "abc", "")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `替换标记内的文字时标记保留`() {
        val result = InlineSpanAdjuster.adjust(listOf(span(0, 3)), "abc", "aXYc")
        assertEquals(listOf(span(0, 4)), result)
    }

    // ---- 规则 6：零长 span（标记中）吸收输入 ----

    @Test
    fun `零长标记吸收随后输入的字`() {
        val result = InlineSpanAdjuster.adjust(listOf(span(2, 2)), "ab", "abX")
        assertEquals(listOf(span(2, 3)), result)
    }

    @Test
    fun `零长标记连续吸收`() {
        var spans = listOf(span(0, 0))
        var text = ""
        for (char in "秩等于") {
            val next = text + char
            spans = InlineSpanAdjuster.adjust(spans, text, next)
            text = next
        }
        assertEquals(listOf(span(0, 3)), spans)
    }

    // ---- 规则 7：换行断开 ----

    @Test
    fun `跨行时在换行处断开`() {
        val result = InlineSpanAdjuster.adjust(listOf(span(0, 2)), "ab", "a\nb")
        assertEquals(listOf(span(0, 1), span(2, 3)), result)
    }

    @Test
    fun `标记内按回车会把标记断开`() {
        // "abc" 的标记 [0,3)，在中间回车 → "ab\nc"
        val result = InlineSpanAdjuster.adjust(listOf(span(0, 3)), "abc", "ab\nc")
        assertEquals(listOf(span(0, 2), span(3, 4)), result)
    }

    // ---- 规则 8：全选删除 ----

    @Test
    fun `全选删除清空所有标记`() {
        val result = InlineSpanAdjuster.adjust(listOf(span(0, 3), span(1, 2)), "abc", "")
        assertTrue(result.isEmpty())
    }

    // ---- 不在标记附近的编辑不影响标记 ----

    @Test
    fun `标记之后的编辑不影响标记`() {
        val result = InlineSpanAdjuster.adjust(listOf(span(0, 1)), "abc", "abXc")
        assertEquals(listOf(span(0, 1)), result)
    }

    @Test
    fun `标记之前的编辑使标记整体平移`() {
        val result = InlineSpanAdjuster.adjust(listOf(span(2, 4)), "abcd", "Xabcd")
        assertEquals(listOf(span(3, 5)), result)
    }

    // ---- 差分的边界 ----

    @Test
    fun `差分能定位单点插入`() {
        val edit = InlineSpanAdjuster.diff("abc", "abXc")
        assertEquals(2, edit.changeStart)
        assertEquals(2, edit.changeEnd)
        assertEquals(1, edit.insertedLength)
    }

    @Test
    fun `差分能定位单点删除`() {
        val edit = InlineSpanAdjuster.diff("abc", "ac")
        assertEquals(1, edit.changeStart)
        assertEquals(2, edit.changeEnd)
        assertEquals(0, edit.insertedLength)
    }

    // ---- 工具条要用的查询与小操作 ----

    @Test
    fun `光标落在标记内能查到该标记`() {
        val spans = listOf(span(2, 5))
        assertEquals(span(2, 5), InlineSpanAdjuster.spanAt(spans, 3))
        assertNull(InlineSpanAdjuster.spanAt(spans, 5))
        assertNull(InlineSpanAdjuster.spanAt(spans, 1))
    }

    @Test
    fun `选区被同一条标记完整覆盖时才能幂等取消`() {
        val spans = listOf(span(2, 5))
        assertEquals(span(2, 5), InlineSpanAdjuster.spanCovering(spans, 3, 4))
        assertNull(InlineSpanAdjuster.spanCovering(spans, 1, 4))
        assertNull(InlineSpanAdjuster.spanCovering(spans, 3, 6))
    }

    // ---- 「标记中」：零长标记必须活到输入落进去为止 ----

    @Test
    fun `normalize 必须保留零长标记`() {
        // 回归守卫：normalize 曾把零长 span 当"空区间"过滤掉，
        // 于是「标记中」在 commit 的那一刻就没了 —— 用户点了色块，接着打字却没有颜色。
        val pending = span(3, 3, EmphasisTone.IDEA)
        assertEquals(listOf(pending), InlineSpanAdjuster.normalize(listOf(pending), "abc"))
    }

    @Test
    fun `零长标记与普通标记共存 并在输入后成形`() {
        val key = InlineSpan(EmphasisTone.KEY, InlineBrush.HIGHLIGHT, 0, 3)
        val pending = span(5, 5, EmphasisTone.IDEA)
        var spans = InlineSpanAdjuster.normalize(listOf(key, pending), "abcde")
        assertEquals(listOf(key, pending), spans)

        spans = InlineSpanAdjuster.adjust(spans, "abcde", "abcdeXY")
        assertEquals(
            listOf(key, InlineSpan(EmphasisTone.IDEA, InlineBrush.HIGHLIGHT, 5, 7)),
            spans
        )
        assertEquals("{{k:abc}}de{{i:XY}}", InlineMarkup.materialize("abcdeXY", spans))
    }

    @Test
    fun `零长标记不会被物化进存储`() {
        val pending = span(2, 2, EmphasisTone.WARN)
        assertEquals("ab", InlineMarkup.materialize("ab", listOf(pending)))
    }

    @Test
    fun `给选区上色会先清掉重叠的旧标记`() {
        val spans = listOf(span(0, 5, EmphasisTone.KEY))
        val result = InlineSpanAdjuster.apply(
            spans, start = 2, end = 4,
            tone = EmphasisTone.WARN, brush = InlineBrush.UNDERLINE,
            visibleText = "abcde"
        )
        assertEquals(
            listOf(
                span(0, 2, EmphasisTone.KEY),
                InlineSpan(EmphasisTone.WARN, InlineBrush.UNDERLINE, 2, 4),
                span(4, 5, EmphasisTone.KEY)
            ),
            result
        )
    }

    @Test
    fun `清除选区只影响被覆盖的部分`() {
        val spans = listOf(span(0, 5))
        val result = InlineSpanAdjuster.clear(spans, start = 1, end = 3, visibleText = "abcde")
        assertEquals(listOf(span(0, 1), span(3, 5)), result)
    }

    @Test
    fun `结束标记中会丢弃未成形的零长标记`() {
        val spans = listOf(span(0, 2), span(2, 2))
        assertEquals(listOf(span(0, 2)), InlineSpanAdjuster.dropCollapsed(spans))
    }

    @Test
    fun `重叠标记会被裁开而不是叠加`() {
        val result = InlineSpanAdjuster.normalize(listOf(span(0, 5), span(3, 8)), "abcdefgh")
        assertEquals(listOf(span(0, 5), span(5, 8)), result)
    }
}
