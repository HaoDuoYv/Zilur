package com.example.zhilu.ui.note.find

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.KnowledgeCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteFindTest {

    private fun card(index: Int, vararg blocks: Block) =
        KnowledgeCard(id = index.toLong(), title = "小节 $index", blocks = blocks.toList())

    @Test
    fun `匹配前先剥离行内语法`() {
        // 若拿原文匹配，「正」会命中 `{{k:正}}` 里的标记结构，且位置会偏移
        val cards = listOf(card(0, Block(id = 1, type = BlockType.TEXT, content = "标准形中{{i:正}}平方项")))
        val hits = findMatches(cards, "正")
        assertEquals(1, hits.size)
        assertEquals(4, hits[0].start)
        assertEquals(5, hits[0].end)
    }

    @Test
    fun `同一块内多处命中都算`() {
        val cards = listOf(card(0, Block(id = 1, type = BlockType.TEXT, content = "abc abc abc")))
        assertEquals(3, findMatches(cards, "abc").size)
    }

    @Test
    fun `大小写不敏感`() {
        val cards = listOf(card(0, Block(id = 1, type = BlockType.TEXT, content = "Diag(λ)")))
        assertEquals(1, findMatches(cards, "diag").size)
        assertEquals(1, findMatches(cards, "DIAG").size)
    }

    @Test
    fun `命中带上所属卡片序号用于跳转`() {
        val cards = listOf(
            card(0, Block(id = 1, type = BlockType.TEXT, content = "无关")),
            card(1, Block(id = 2, type = BlockType.TEXT, content = "这里有惯性定理")),
            card(2, Block(id = 3, type = BlockType.TEXT, content = "惯性指数"))
        )
        val hits = findMatches(cards, "惯性")
        assertEquals(2, hits.size)
        assertEquals(listOf(1, 2), hits.map { it.cardIndex })
        assertEquals(listOf(2L, 3L), hits.map { it.blockId })
    }

    @Test
    fun `图片与分割线不参与匹配`() {
        val cards = listOf(
            card(
                0,
                Block(id = 1, type = BlockType.IMAGE, content = "abc"),
                Block(id = 2, type = BlockType.DIVIDER, content = "abc")
            )
        )
        assertTrue(findMatches(cards, "abc").isEmpty())
    }

    @Test
    fun `空白关键词不产生命中`() {
        val cards = listOf(card(0, Block(id = 1, type = BlockType.TEXT, content = "abc")))
        assertTrue(findMatches(cards, "").isEmpty())
        assertTrue(findMatches(cards, "   ").isEmpty())
    }

    @Test
    fun `分支子块也会被搜到`() {
        val cards = listOf(
            card(
                0,
                Block(id = 1, type = BlockType.BRANCH, content = "父分支"),
                Block(id = 2, type = BlockType.TEXT, content = "子块里的惯性定理", parentBranchId = 1)
            )
        )
        val hits = findMatches(cards, "惯性定理")
        assertEquals(1, hits.size)
        assertEquals(2L, hits[0].blockId)
    }
}
