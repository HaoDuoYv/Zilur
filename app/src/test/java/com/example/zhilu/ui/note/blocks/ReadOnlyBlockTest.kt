package com.example.zhilu.ui.note.blocks

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadOnlyBlockTest {
    @Test
    fun `copy menu label is Chinese`() {
        assertEquals("复制此块", readOnlyBlockCopyMenuLabel())
    }

    @Test
    fun `clipboard text matches block content exactly`() {
        val block = Block(
            type = BlockType.TEXT,
            content = " first line\nsecond line "
        )

        assertEquals(block.content, readOnlyBlockClipboardText(block).text)
    }
}
