package com.example.zhilu.data.local.mapper

import com.example.zhilu.data.local.entity.NoteCardEntity
import com.example.zhilu.domain.model.KnowledgeCard

object CardMapper {
    fun toDomain(entity: NoteCardEntity, blocks: List<com.example.zhilu.domain.model.Block> = emptyList()): KnowledgeCard =
        KnowledgeCard(
            id = entity.id,
            title = entity.title,
            blocks = blocks,
            isExpanded = true,
            isFocused = false
        )

    fun toEntity(domain: KnowledgeCard, noteId: Long = 0, sortOrder: Int = 0): NoteCardEntity = NoteCardEntity(
        id = domain.id,
        noteId = noteId,
        title = domain.title,
        sortOrder = sortOrder
    )
}
