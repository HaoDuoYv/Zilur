package com.example.zhilu.data.ai

import com.example.zhilu.domain.ai.model.AiRef
import com.example.zhilu.domain.ai.model.AiRefKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「引用 / 引用消息 / 附件」注入 user 消息的**段落形态与顺序**。
 *
 * 这些断言就是模型看到的最后一道形状：顺序错了（上下文跑到问题后面）、
 * 段落头没了（模型看不出这是引用而不是用户的正文）都会直接让「引用失效」复发。
 */
class AiUserMessageTextTest {

    @Test
    fun `无任何上下文时原样返回正文`() {
        assertEquals("帮我总结一下", AiUserMessageText.build("帮我总结一下", null, emptyList(), null))
    }

    @Test
    fun `引用内容在问题之前 且带段落头`() {
        val text = AiUserMessageText.build(
            base = "帮我据此出两道题",
            fileText = null,
            refs = listOf(ref("笔记《二次型》\n\n正定矩阵的定义")),
            quotedText = null
        )
        val header = text.indexOf("【用户引用的知识内容】")
        val content = text.indexOf("正定矩阵的定义")
        val question = text.indexOf("帮我据此出两道题")
        assertTrue("段落头必须存在", header >= 0)
        assertTrue("引用内容要在段落头之后", content > header)
        assertTrue("问题要跟在引用内容之后（贴近生成位置）", question > content)
    }

    @Test
    fun `多条引用按序保留 且都进同一条消息`() {
        val text = AiUserMessageText.build(
            base = "对比一下",
            fileText = null,
            refs = listOf(ref("笔记《A》"), ref("笔记《B》")),
            quotedText = null
        )
        val a = text.indexOf("笔记《A》")
        val b = text.indexOf("笔记《B》")
        assertTrue(a in 0 until b)
    }

    @Test
    fun `引用消息与附件各成一段 附件在最后`() {
        val text = AiUserMessageText.build(
            base = "展开说说",
            fileText = "附件正文",
            refs = emptyList(),
            quotedText = "上一轮 AI 的回答"
        )
        val quoted = text.indexOf("【引用的消息】")
        val question = text.indexOf("展开说说")
        val file = text.indexOf("【附件内容】")
        assertTrue(quoted >= 0)
        assertTrue(question > quoted)
        assertTrue(file > question)
    }

    @Test
    fun `完整顺序为 引用内容 引用消息 问题 附件`() {
        val text = AiUserMessageText.build(
            base = "问题",
            fileText = "附件",
            refs = listOf(ref("知识快照")),
            quotedText = "被引用消息"
        )
        val refs = text.indexOf("【用户引用的知识内容】")
        val quoted = text.indexOf("【引用的消息】")
        val question = text.indexOf("问题")
        val file = text.indexOf("【附件内容】")
        assertTrue(refs in 0 until quoted)
        assertTrue(quoted < question)
        assertTrue(question < file)
    }

    @Test
    fun `快照为空的引用退化为不可用提示 而不是整段消失`() {
        val text = AiUserMessageText.build("问题", null, listOf(ref("")), null)
        assertTrue(text.contains(AiUserMessageText.UNAVAILABLE))
    }

    @Test
    fun `引用总量超限按序截断并标注省略`() {
        val first = "A".repeat(AiUserMessageText.REF_TOTAL_MAX_CHARS - 4_000)
        val second = "B".repeat(5_000)
        val text = AiUserMessageText.build("问题", null, listOf(ref(first), ref(second)), null)
        // 第一条吃满后只剩 4000 额度：第二条被截到 4000，其余省略。
        assertTrue(text.contains("B".repeat(4_000)))
        assertFalse(text.contains("B".repeat(4_001)))
        assertTrue(text.contains(AiUserMessageText.OMITTED_SUFFIX))
        assertTrue(text.contains("问题"))
    }

    @Test
    fun `引用消息超长截断并标注`() {
        val quoted = "Q".repeat(AiUserMessageText.QUOTED_MAX_CHARS + 500)
        val text = AiUserMessageText.build("问题", null, emptyList(), quoted)
        assertTrue(text.contains("Q".repeat(AiUserMessageText.QUOTED_MAX_CHARS) + AiUserMessageText.TRUNCATED_SUFFIX))
        assertFalse(text.contains("Q".repeat(AiUserMessageText.QUOTED_MAX_CHARS + 1)))
    }

    @Test
    fun `空白正文不产生空段`() {
        assertEquals("", AiUserMessageText.build("   ", null, emptyList(), null))
        assertEquals("", AiUserMessageText.build("", "  ", emptyList(), "   "))
    }

    private fun ref(snapshot: String) = AiRef(
        kind = AiRefKind.NOTE,
        noteId = 1L,
        title = "标题",
        snapshot = snapshot
    )
}
