package com.example.zhilu.ui.home

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotePreviewTextTest {
    @Test
    fun prefersTextOverOtherBlocks() {
        val note = note(
            Block(type = BlockType.IMAGE, content = "img", sortOrder = 0),
            Block(type = BlockType.TEXT, content = "正文摘要", sortOrder = 1)
        )
        assertEquals("正文摘要", notePreviewText(note))
    }

    @Test
    fun fallsBackToBranchThenCode() {
        val note = note(
            Block(type = BlockType.BRANCH, content = "分支标题", sortOrder = 0),
            Block(type = BlockType.CODE, content = "line1\nline2", sortOrder = 1)
        )
        assertEquals("分支标题", notePreviewText(note))
    }

    @Test
    fun codePreviewUsesFirstLine() {
        val note = note(
            Block(type = BlockType.CODE, content = "fun main() {\n  println(1)\n}", sortOrder = 0)
        )
        assertEquals("fun main() {", notePreviewText(note))
    }

    @Test
    fun emptyNoteHasNoPreview() {
        assertNull(notePreviewText(note()))
    }

    private fun note(vararg blocks: Block): Note = Note(
        id = 1L,
        title = "t",
        blocks = blocks.toList()
    )
}
