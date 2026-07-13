package com.example.zhilu.data.local.mapper

import com.example.zhilu.data.local.entity.NoteBlockEntity
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType

object BlockMapper {
    fun toDomain(entity: NoteBlockEntity): Block = Block(
        id = entity.id,
        noteId = entity.noteId,
        cardId = entity.cardId,
        type = BlockType.fromValue(entity.type),
        content = entity.content,
        language = entity.language,
        sortOrder = entity.sortOrder,
        parentBranchId = entity.parentBranchId
    )

    fun toEntity(domain: Block): NoteBlockEntity = NoteBlockEntity(
        id = domain.id,
        noteId = domain.noteId,
        cardId = domain.cardId,
        type = domain.type.value,
        content = domain.content,
        language = domain.language,
        sortOrder = domain.sortOrder,
        parentBranchId = domain.parentBranchId
    )
}
