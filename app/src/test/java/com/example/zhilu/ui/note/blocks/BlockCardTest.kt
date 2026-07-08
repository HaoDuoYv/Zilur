package com.example.zhilu.ui.note.blocks

import com.example.zhilu.domain.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockCardTest {
    @Test
    fun `blockTypeLabel returns Chinese labels for every block type`() {
        val labels = BlockType.entries.associateWith(::blockTypeLabel)

        assertEquals(
            mapOf(
                BlockType.TEXT to "文本",
                BlockType.IMAGE to "图片",
                BlockType.LINK to "链接",
                BlockType.LATEX to "公式",
                BlockType.CODE to "代码",
                BlockType.DIVIDER to "分割线",
                BlockType.TODO to "待办"
            ),
            labels
        )
    }
}
