package com.example.zhilu.domain.usecase.clipboard

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BlockClipboardSerializerTest {

    @Test
    fun `serializes and deserializes text block without persistent ids`() {
        val block = Block(
            id = 1L, noteId = 2L, cardId = 3L,
            type = BlockType.TEXT, content = "hello", sortOrder = 5
        )
        val json = BlockClipboardSerializer.toJson(block)
        assertFalse(json.contains("\"id\":1"))
        assertFalse(json.contains("\"noteId\":2"))
        assertFalse(json.contains("\"cardId\":3"))
        assertFalse(json.contains("\"sortOrder\":5"))

        val restored = BlockClipboardSerializer.fromJson(json)
        assertEquals(BlockType.TEXT, restored?.type)
        assertEquals("hello", restored?.content)
        assertEquals(0L, restored?.id)
        assertEquals(0L, restored?.noteId)
    }

    @Test
    fun `serializes branch with children recursively`() {
        val child = Block(id = 2L, type = BlockType.TEXT, content = "child", parentBranchId = 1L)
        val branch = Block(id = 1L, type = BlockType.BRANCH, content = "branch", parentBranchId = null)
        val json = BlockClipboardSerializer.toJson(branch, listOf(child))
        val restored = BlockClipboardSerializer.fromJson(json)
        assertEquals(1, restored?.children?.size)
        assertEquals("child", restored?.children?.first()?.content)
    }
}
