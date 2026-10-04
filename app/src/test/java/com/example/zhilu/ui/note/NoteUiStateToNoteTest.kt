package com.example.zhilu.ui.note

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.KnowledgeCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 保存路径上最容易「一次往返删掉整篇正文」的那一处：`NoteUiState.toNote()`。
 *
 * 笔记正文在库里有两个**互斥**的落点（`Note.contentBlocks` 的语义是"blocks 非空就只用 blocks"）：
 *
 * - 有知识卡片时，块挂在卡片下，读取时 `Note.blocks` 是空的；
 * - 没有卡片时，块直接归属笔记。
 *
 * 而 `toNote()` 走的是"**把块展平、并逐个挂回它所属卡片的 id**"这条路 —— 也就是说
 * 第 60 行的 `cardId = card.id` 是把块挂回卡片的**唯一**落点。仓库层
 * `NoteRepositoryImpl.replaceBlocks` 靠 `cardMap[block.cardId]` 找归属，一旦这里漏填或填错，
 * 块会变成"没有卡片的孤儿"，下一次 hydrate 就被并进第一张卡片（甚至丢掉），
 * 表现为"只是改了个标题，正文没了"。
 *
 * 这条断言此前没有专属测试守着，属于纯赚。
 */
class NoteUiStateToNoteTest {

    private fun block(id: Long, content: String, cardId: Long? = null) = Block(
        id = id,
        type = BlockType.TEXT,
        content = content,
        cardId = cardId
    )

    @Test
    fun `每张卡片的块都被挂回自己卡片的 id`() {
        val state = NoteUiState(
            noteId = 7L,
            title = "标题",
            cards = listOf(
                KnowledgeCard(id = 101L, title = "第一节", blocks = listOf(block(1, "A1"), block(2, "A2"))),
                KnowledgeCard(id = 202L, title = "第二节", blocks = listOf(block(3, "B1")))
            )
        )

        val note = state.toNote()

        assertEquals(listOf(101L, 101L, 202L), note.blocks.map { it.cardId })
        // 展平顺序必须与卡片顺序一致，否则正文会被打乱
        assertEquals(listOf("A1", "A2", "B1"), note.blocks.map { it.content })
    }

    @Test
    fun `展平后的 sortOrder 单调递增_卡片之间留出间隔`() {
        val state = NoteUiState(
            cards = listOf(
                KnowledgeCard(id = 1L, title = "一", blocks = listOf(block(1, "A"), block(2, "B"))),
                KnowledgeCard(id = 2L, title = "二", blocks = listOf(block(3, "C")))
            )
        )

        val orders = state.toNote().blocks.map { it.sortOrder }
        assertEquals(orders.sorted(), orders)
        assertTrue("第二张卡片要和第一张拉开，不能连号", orders[2] > orders[1])
    }

    @Test
    fun `没有卡片时块挂在笔记本身_不伪造卡片 id`() {
        val state = NoteUiState(
            noteId = 5L,
            title = "扁平笔记",
            blocks = listOf(block(1, "只有一段"), block(2, "又一段")),
            cards = emptyList()
        )

        val note = state.toNote()

        // 扁平笔记会补一张**隐式单卡**（id=0）承载正文：库里 `cardId = 0` 表示"不挂卡片"
        assertEquals(2, note.blocks.size)
        assertEquals(listOf(0L, 0L), note.blocks.map { it.cardId })
        assertEquals(1, note.cards.size)
    }

    @Test
    fun `正文与卡片同时带出_note 的两个落点都填好`() {
        val state = NoteUiState(
            noteId = 9L,
            title = "标题",
            cards = listOf(KnowledgeCard(id = 3L, title = "节", blocks = listOf(block(1, "X"))))
        )

        val note = state.toNote()

        // blocks 非空即为准（contentBlocks 的判据），所以这里必须是"已展平且挂了 cardId"的版本
        assertTrue(note.blocks.isNotEmpty())
        assertEquals(1, note.cards.size)
        assertEquals(9L, note.id)
        assertEquals("标题", note.title)
    }
}
