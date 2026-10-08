package com.example.zhilu.domain.ai

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRefSnapshotTest {

    @Test
    fun `整篇笔记快照带标题 正文按序拼接 不写内部 id`() {
        val snapshot = AiRefSnapshot.forNote(
            title = "二次型",
            blocks = listOf(block("第一段"), block("第二段"))
        )
        assertTrue(snapshot.startsWith("笔记《二次型》"))
        val first = snapshot.indexOf("第一段")
        val second = snapshot.indexOf("第二段")
        assertTrue(first in 0 until second)
        // 内部 id 对模型没有价值，未保存内容的负数 id 还会误导它 —— 一个都不许出现。
        assertFalse(snapshot.contains("id="))
    }

    @Test
    fun `未命名笔记与卡片有兜底标题`() {
        assertTrue(AiRefSnapshot.forNote("", listOf(block("x"))).contains("《未命名笔记》"))
        assertTrue(AiRefSnapshot.forCard("", "", listOf(block("x"))).contains("《未命名卡片》"))
    }

    @Test
    fun `卡片与块快照带归属上下文`() {
        val card = AiRefSnapshot.forCard("线代", "正交", listOf(block("正文")))
        assertTrue(card.contains("笔记《线代》"))
        assertTrue(card.contains("卡片《正交》"))
        assertTrue(card.contains("正文"))

        val blockSnapshot = AiRefSnapshot.forBlock("线代", block("片段"))
        assertTrue(blockSnapshot.contains("笔记《线代》"))
        assertTrue(blockSnapshot.contains("片段"))
    }

    @Test
    fun `八种块类型各自的渲染形态`() {
        assertEquals(
            "```kotlin\nval a = 1\n```",
            AiRefSnapshot.formatBlock(block("val a = 1", BlockType.CODE, language = "kotlin"))
        )
        assertEquals(
            "```text\n裸代码\n```",
            AiRefSnapshot.formatBlock(block("裸代码", BlockType.CODE))
        )
        assertEquals("\$\$x^2\$\$", AiRefSnapshot.formatBlock(block("x^2", BlockType.LATEX)))
        assertEquals(
            "https://example.com",
            AiRefSnapshot.formatBlock(block("https://example.com", BlockType.LINK))
        )
        assertEquals("- [ ] 待办", AiRefSnapshot.formatBlock(block("待办", BlockType.TODO)))
        assertEquals("---", AiRefSnapshot.formatBlock(block("", BlockType.DIVIDER)))
        assertEquals("【分支】推导", AiRefSnapshot.formatBlock(block("推导", BlockType.BRANCH)))
        assertEquals(
            "【图片】file://a.png",
            AiRefSnapshot.formatBlock(block("5|file://a.png", BlockType.IMAGE))
        )
        assertEquals("普通文字", AiRefSnapshot.formatBlock(block("普通文字")))
    }

    @Test
    fun `超长内容截断并标注`() {
        val long = "字".repeat(AiRefSnapshot.MAX_CHARS + 100)
        val snapshot = AiRefSnapshot.forBlock("笔记", block(long))
        assertTrue(snapshot.length < AiRefSnapshot.MAX_CHARS + 200)
        assertTrue(snapshot.endsWith("…（已截断）"))
    }

    private fun block(
        content: String,
        type: BlockType = BlockType.TEXT,
        language: String = ""
    ) = Block(type = type, content = content, language = language)
}
