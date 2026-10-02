package com.example.zhilu.domain.markup

import com.example.zhilu.domain.model.EmphasisTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 手打辅助（§3.11.4）的纯逻辑测试。
 *
 * 关键性质：转换后 `visibleText` 里**不再包含任何语法字符**，
 * 且返回区间正好覆盖被标记的正文——这两点决定了它能否安全地塞进 span 表。
 */
class TypedMarkerTest {

    @Test
    fun `末尾的完整标记被识别并去掉语法字符`() {
        val typed = typedMarkerAtEnd("标准形中{{k:正}}")
        assertEquals("标准形中正", typed?.visibleText)
        assertEquals(EmphasisTone.KEY, typed?.tone)
        assertEquals(InlineBrush.HIGHLIGHT, typed?.brush)
        assertEquals(4..4, typed?.range)
    }

    @Test
    fun `四种角色都认`() {
        assertEquals(EmphasisTone.KEY, typedMarkerAtEnd("{{k:a}}")?.tone)
        assertEquals(EmphasisTone.IDEA, typedMarkerAtEnd("{{i:a}}")?.tone)
        assertEquals(EmphasisTone.WARN, typedMarkerAtEnd("{{w:a}}")?.tone)
        assertEquals(EmphasisTone.TODO, typedMarkerAtEnd("{{t:a}}")?.tone)
    }

    @Test
    fun `笔刷后缀被认出来`() {
        assertEquals(InlineBrush.COLOR, typedMarkerAtEnd("{{k-c:a}}")?.brush)
        assertEquals(InlineBrush.UNDERLINE, typedMarkerAtEnd("{{k-u:a}}")?.brush)
    }

    @Test
    fun `多字正文的区间正确`() {
        val typed = typedMarkerAtEnd("ab{{i:要点}}")
        assertEquals("ab要点", typed?.visibleText)
        assertEquals(2..3, typed?.range)
    }

    @Test
    fun `没打完的标记不动`() {
        assertNull(typedMarkerAtEnd("{{k:正"))
        assertNull(typedMarkerAtEnd("{{k:"))
        assertNull(typedMarkerAtEnd("{{"))
    }

    @Test
    fun `标记不在末尾时不动（已知边界）`() {
        // 人是顺序输入的，敲下闭合的那一刻标记必然在末尾；中间的标记不转换
        assertNull(typedMarkerAtEnd("{{k:正}}后面还有字"))
    }

    @Test
    fun `未知角色不认`() {
        assertNull(typedMarkerAtEnd("{{z:a}}"))
    }

    @Test
    fun `空正文不认`() {
        assertNull(typedMarkerAtEnd("{{k:}}"))
    }

    @Test
    fun `正文里带花括号或换行不认`() {
        assertNull(typedMarkerAtEnd("{{k:a{b}}"))
        assertNull(typedMarkerAtEnd("{{k:a\nb}}"))
    }

    @Test
    fun `连着打两个标记时只转换最后那个`() {
        val typed = typedMarkerAtEnd("{{k:a}}{{i:b}}")
        assertEquals("{{k:a}}b", typed?.visibleText)
        assertEquals(EmphasisTone.IDEA, typed?.tone)
    }

    @Test
    fun `与划线工具条产出的形态一致（物化结果可被解析回去）`() {
        val typed = typedMarkerAtEnd("正定{{w:注意}}")
        val materialized = InlineMarkup.materialize(
            typed!!.visibleText,
            listOf(InlineSpan(typed.tone, typed.brush, typed.range.first, typed.range.last + 1))
        )
        val reparsed = InlineMarkup.parseSpans(materialized)
        assertEquals("正定注意", reparsed.visibleText)
        assertEquals(1, reparsed.spans.size)
        assertEquals(EmphasisTone.WARN, reparsed.spans[0].tone)
    }
}
