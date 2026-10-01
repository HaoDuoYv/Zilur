package com.example.zhilu.ui.note

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.KnowledgeCard
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.model.TodoItem

enum class SaveStatus {
    IDLE,
    SAVING,
    SAVED,
    ERROR
}

data class NoteUiState(
    val noteId: Long = 0L,
    val title: String = "",
    val blocks: List<Block> = listOf(
        Block(type = BlockType.TEXT, content = "", sortOrder = 0)
    ),
    val cards: List<KnowledgeCard> = emptyList(),
    val activeCardId: Long? = null,
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
    val saveStatus: SaveStatus = SaveStatus.IDLE,
    val lastSavedAt: Long? = null,
    val isProcessingImage: Boolean = false,
    val branchExpandedStates: Map<Long, Boolean> = emptyMap(),
    /** 正被 AI 生成占用的卡片 id 集合（驱动「生成中」动画与禁点）。 */
    val generatingCardIds: Set<Long> = emptySet(),
    /** 正被 AI 生成占用的块 id 集合。 */
    val generatingBlockIds: Set<Long> = emptySet(),
    val error: String? = null
) {
    fun toNote(): Note {
        val effectiveCards = cards.takeIf { it.isNotEmpty() }
            ?: listOf(
                KnowledgeCard(
                    id = 0L,
                    title = title,
                    blocks = blocks,
                    isExpanded = true,
                    isFocused = false
                )
            )
        val flattenedBlocks = effectiveCards.flatMapIndexed { cardIndex, card ->
            card.blocks.mapIndexed { blockIndex, block ->
                block.copy(
                    cardId = card.id,
                    sortOrder = cardIndex * 10_000 + blockIndex
                )
            }
        }
        return Note(
            id = noteId,
            title = title,
            updatedAt = System.currentTimeMillis(),
            blocks = flattenedBlocks,
            cards = effectiveCards,
            tags = selectedTags
        )
    }
}
