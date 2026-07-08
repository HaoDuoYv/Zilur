package com.example.zhilu.data.local.mapper

import com.example.zhilu.data.local.entity.NoteBlockEntity
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockMapperTest {
    @Test
    fun mapsLanguageBothDirections() {
        val domain = Block(
            id = 11,
            noteId = 7,
            type = BlockType.CODE,
            content = "println(42)",
            language = "kotlin",
            sortOrder = 3
        )

        val entity = BlockMapper.toEntity(domain)
        val mappedDomain = BlockMapper.toDomain(
            NoteBlockEntity(
                id = entity.id,
                noteId = entity.noteId,
                type = entity.type,
                content = entity.content,
                language = entity.language,
                sortOrder = entity.sortOrder
            )
        )

        assertEquals("kotlin", entity.language)
        assertEquals("kotlin", mappedDomain.language)
    }
}
