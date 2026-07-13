package com.example.zhilu.data.local.mapper

import com.example.zhilu.data.local.entity.NoteEntity
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.KnowledgeCard
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.Tag

object NoteMapper {
    fun toDomain(
        entity: NoteEntity,
        blocks: List<Block> = emptyList(),
        cards: List<KnowledgeCard> = emptyList(),
        tags: List<Tag> = emptyList()
    ): Note = Note(
        id = entity.id,
        title = entity.title,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        isFavorite = entity.isFavorite,
        deletedAt = entity.deletedAt,
        blocks = blocks,
        cards = cards,
        tags = tags
    )

    fun toEntity(domain: Note): NoteEntity = NoteEntity(
        id = domain.id,
        title = domain.title,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt,
        isFavorite = domain.isFavorite,
        deletedAt = domain.deletedAt
    )
}
