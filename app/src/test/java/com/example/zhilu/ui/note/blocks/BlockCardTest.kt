package com.example.zhilu.ui.note.blocks

import com.example.zhilu.domain.model.BlockType
import androidx.compose.material3.SwipeToDismissBoxValue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `shouldShowBlockActionMenu returns true when any edit action exists`() {
        assertTrue(shouldShowBlockActionMenu(isEditing = true, hasDelete = true, hasMoveUp = false, hasMoveDown = false))
        assertTrue(shouldShowBlockActionMenu(isEditing = true, hasDelete = false, hasMoveUp = true, hasMoveDown = false))
        assertTrue(shouldShowBlockActionMenu(isEditing = true, hasDelete = false, hasMoveUp = false, hasMoveDown = true))

        assertFalse(shouldShowBlockActionMenu(isEditing = true, hasDelete = false, hasMoveUp = false, hasMoveDown = false))
        assertFalse(shouldShowBlockActionMenu(isEditing = false, hasDelete = true, hasMoveUp = true, hasMoveDown = true))
    }

    @Test
    fun `swipe delete requests removal without confirming dismissed state`() {
        assertTrue(shouldRequestSwipeDelete(SwipeToDismissBoxValue.EndToStart))
        assertFalse(shouldRequestSwipeDelete(SwipeToDismissBoxValue.StartToEnd))
        assertFalse(shouldRequestSwipeDelete(SwipeToDismissBoxValue.Settled))

        assertFalse(shouldConfirmSwipeValueChange(SwipeToDismissBoxValue.EndToStart))
        assertFalse(shouldConfirmSwipeValueChange(SwipeToDismissBoxValue.StartToEnd))
        assertTrue(shouldConfirmSwipeValueChange(SwipeToDismissBoxValue.Settled))
    }
}
