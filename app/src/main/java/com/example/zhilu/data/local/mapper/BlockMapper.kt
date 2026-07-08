package com.example.zhilu.data.local.mapper

import com.example.zhilu.data.local.entity.NoteBlockEntity
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType

object BlockMapper {
    fun toDomain(entity: NoteBlockEntity): Block = Block(
        id = entity.id,
        noteId = entity.noteId,
        type = BlockType.fromValue(entity.type),
        content = entity.content,
        sortOrder = entity.sortOrder
    )

    fun toEntity(domain: Block): NoteBlockEntity = NoteBlockEntity(
        id = domain.id,
        noteId = domain.noteId,
        type = domain.type.value,
        content = domain.content,
        sortOrder = domain.sortOrder
    )
}
