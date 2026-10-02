package com.example.zhilu.data.local.mapper

import com.example.zhilu.data.local.entity.NoteBlockEntity
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.EmphasisTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun mapsEmphasisBothDirections() {
        val domain = Block(
            id = 11,
            noteId = 7,
            type = BlockType.TEXT,
            content = "只判顺序主子式不够",
            sortOrder = 0,
            emphasis = EmphasisTone.WARN
        )

        val entity = BlockMapper.toEntity(domain)
        assertEquals(EmphasisTone.WARN.value, entity.emphasis)
        assertEquals(EmphasisTone.WARN, BlockMapper.toDomain(entity).emphasis)
    }

    @Test
    fun unmappedEmphasisBecomesZeroAndRoundTripsAsNull() {
        // 未标记 → 落库 0
        val plain = Block(id = 1, noteId = 1, content = "正文")
        assertEquals(EmphasisTone.NONE_VALUE, BlockMapper.toEntity(plain).emphasis)
        // 0 → 读回未标记
        assertNull(BlockMapper.toDomain(BlockMapper.toEntity(plain)).emphasis)
    }

    @Test
    fun everyToneSurvivesTheRoundTrip() {
        for (tone in EmphasisTone.entries) {
            val block = Block(id = 1, noteId = 1, content = "x", emphasis = tone)
            assertEquals(tone, BlockMapper.toDomain(BlockMapper.toEntity(block)).emphasis)
        }
    }
}
