package com.example.zhilu.domain.markup

import com.example.zhilu.domain.model.EmphasisTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 打字路径的转移测试（§3.7 + §3.11.4）。
 *
 * 这组测试顶替了"必须用 adb 点中输入框才能验证"的部分：真机上点不中那个输入框时，
 * 至少"标记有没有被转换、缓冲区里还剩什么"这两件事仍由单测钉死。
 */
class EditorTextTransitionTest {

    @Test
    fun `普通输入在标记末尾会扩展该标记（规则 2）`() {
        val spans = listOf(InlineSpan(EmphasisTone.KEY, InlineBrush.HIGHLIGHT, 0, 2))
        val next = applyTypedText("要点", spans, "要点ABC", 5)
        assertEquals("要点ABC", next.text)
        assertEquals(1, next.spans.size)
        // 规则 2：在 span 末尾插入 = "我在接着写这个要点"，所以区间跟着长
        assertEquals(0, next.spans[0].start)
        assertEquals(5, next.spans[0].end)
        assertEquals(5, next.caret)
    }

    @Test
    fun `在标记前面插入会把区间推后`() {
        val spans = listOf(InlineSpan(EmphasisTone.KEY, InlineBrush.HIGHLIGHT, 0, 2))
        val next = applyTypedText("要点", spans, "XX要点", 4)
        assertEquals("XX要点", next.text)
        assertEquals(2, next.spans[0].start)
        assertEquals(4, next.spans[0].end)
    }

    @Test
    fun `手打完整标记被转成 span 且语法字符消失`() {
        val next = applyTypedText("正定", emptyList(), "正定{{k:要点}}", 10)
        assertEquals("正定要点", next.text)
        assertEquals(1, next.spans.size)
        assertEquals(EmphasisTone.KEY, next.spans[0].tone)
        assertEquals(2, next.spans[0].start)
        assertEquals(4, next.spans[0].end)
        assertEquals(4, next.caret)
    }

    @Test
    fun `手打标记不会破坏已有 span`() {
        val spans = listOf(InlineSpan(EmphasisTone.WARN, InlineBrush.HIGHLIGHT, 0, 2))
        val next = applyTypedText("注意", spans, "注意{{i:想法}}", 10)
        assertEquals("注意想法", next.text)
        assertEquals(2, next.spans.size)
        assertEquals(EmphasisTone.WARN, next.spans[0].tone)
        assertEquals(0, next.spans[0].start)
        assertEquals(2, next.spans[0].end)
        assertEquals(EmphasisTone.IDEA, next.spans[1].tone)
        assertEquals(2, next.spans[1].start)
        assertEquals(4, next.spans[1].end)
    }

    @Test
    fun `手打带笔刷后缀的标记`() {
        val next = applyTypedText("", emptyList(), "{{t-u:交作业}}", 10)
        assertEquals("交作业", next.text)
        assertEquals(EmphasisTone.TODO, next.spans[0].tone)
        assertEquals(InlineBrush.UNDERLINE, next.spans[0].brush)
    }

    @Test
    fun `没打完的标记原样留在缓冲区`() {
        // "正定{{k:要" = 7 个字符
        val next = applyTypedText("正定", emptyList(), "正定{{k:要", 7)
        assertEquals("正定{{k:要", next.text)
        assertTrue(next.spans.isEmpty())
        assertEquals(7, next.caret)
    }

    @Test
    fun `转换后的形态与划词工具条一致（物化后可解析回去）`() {
        val next = applyTypedText("正定", emptyList(), "正定{{w:注意}}", 10)
        val stored = InlineMarkup.materialize(next.text, next.spans)
        val reparsed = InlineMarkup.parseSpans(stored)
        assertEquals("正定注意", reparsed.visibleText)
        assertEquals(EmphasisTone.WARN, reparsed.spans.single().tone)
    }

    @Test
    fun `全选删除会清空 span`() {
        val spans = listOf(InlineSpan(EmphasisTone.KEY, InlineBrush.HIGHLIGHT, 0, 2))
        val next = applyTypedText("要点", spans, "", 0)
        assertEquals("", next.text)
        assertTrue(next.spans.isEmpty())
    }

    @Test
    fun `光标越界会被夹回范围`() {
        val next = applyTypedText("ab", emptyList(), "ab", 99)
        assertEquals(2, next.caret)
    }
}
