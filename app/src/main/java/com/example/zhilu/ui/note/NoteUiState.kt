package com.example.zhilu.ui.note

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.model.TodoItem

data class NoteUiState(
    val noteId: Long = 0L,
    val title: String = "",
    val blocks: List<Block> = listOf(
        Block(type = BlockType.TEXT, content = "", sortOrder = 0)
    ),
    val selectedTags: List<Tag> = emptyList(),
    val availableTags: List<Tag> = emptyList(),
    val todoItems: List<TodoItem> = emptyList(),
    val showCompletedTodos: Boolean = false,
    val reviewPlan: ReviewPlan? = null,
    val isReviewDue: Boolean = false,
    val isRecordingReview: Boolean = false,
    val isEditing: Boolean = true,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val lastSavedAt: Long? = null,
    val error: String? = null
) {
    fun toNote(): Note = Note(
        id = noteId,
        title = title,
        updatedAt = System.currentTimeMillis(),
        blocks = blocks.mapIndexed { index, block -> block.copy(sortOrder = index) },
        tags = selectedTags
    )
}
