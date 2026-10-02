package com.example.zhilu.ui.note.knowledge

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.KnowledgeCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardSummaryTest {

    private fun card(vararg blocks: Block) = KnowledgeCard(id = 1, title = "小节", blocks = blocks.toList())

    @Test
    fun `先剥离行内语法再取摘要`() {
        val summary = cardSummary(
            card(
                Block(type = BlockType.TEXT, content = "标准形中{{i:正}}平方项的个数由二次型{{k:唯一确定}}。后半句")
            )
        )
        assertEquals("标准形中正平方项的个数由二次型唯一确定", summary)
    }

    @Test
    fun `只取首句`() {
        val summary = cardSummary(card(Block(type = BlockType.TEXT, content = "第一句。第二句。")))
        assertEquals("第一句", summary)
    }

    @Test
    fun `超长时截断并加省略号`() {
        val long = "一二三四五六七八九十".repeat(6)
        val summary = cardSummary(card(Block(type = BlockType.TEXT, content = long)), maxChars = 10)
        assertEquals("一二三四五六七八九十…", summary)
    }

    @Test
    fun `没有 TEXT 时回退到分支标题`() {
        val summary = cardSummary(card(Block(type = BlockType.BRANCH, content = "三种情形")))
        assertEquals("三种情形", summary)
    }

    @Test
    fun `分支子块不参与摘要`() {
        val summary = cardSummary(
            card(
                Block(id = 1, type = BlockType.BRANCH, content = "父分支"),
                Block(id = 2, type = BlockType.TEXT, content = "子块正文", parentBranchId = 1)
            )
        )
        assertEquals("父分支", summary)
    }

    @Test
    fun `空卡片没有摘要`() {
        assertNull(cardSummary(card()))
        assertNull(cardSummary(card(Block(type = BlockType.TEXT, content = "   "))))
        assertNull(cardSummary(card(Block(type = BlockType.DIVIDER, content = ""))))
    }

    @Test
    fun `换行会被压成空格`() {
        val summary = cardSummary(card(Block(type = BlockType.TEXT, content = "第一行\n第二行")))
        assertEquals("第一行 第二行", summary)
    }
}
