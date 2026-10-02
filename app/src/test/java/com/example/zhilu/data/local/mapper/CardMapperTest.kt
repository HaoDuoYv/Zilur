package com.example.zhilu.data.local.mapper

import com.example.zhilu.domain.model.KnowledgeCard
import org.junit.Assert.assertEquals
import org.junit.Test

class CardMapperTest {

    @Test
    fun `toNewEntity zeroes the in-memory card id so the database assigns the primary key`() {
        // 回归守卫：内存里的卡片 id 是临时值（新建卡片为负数）。若原样落库，
        // 它会成为真实主键，与后续会话重新分配的临时 id 撞号，
        // 导致卡片列表出现重复 key 而崩溃。
        val entity = CardMapper.toNewEntity(
            domain = KnowledgeCard(id = -3L, title = "卡片"),
            noteId = 7L,
            sortOrder = 2
        )

        assertEquals("临时 id 必须归零", 0L, entity.id)
        assertEquals(7L, entity.noteId)
        assertEquals("卡片", entity.title)
        assertEquals(2, entity.sortOrder)
    }

    @Test
    fun `toNewEntity keeps positive production ids out of the entity as well`() {
        // 所有卡片在写入前都会被删除并按顺序重建，因此即使是已有卡片也一律重新分配主键。
        val entity = CardMapper.toNewEntity(
            domain = KnowledgeCard(id = 99L, title = "已有卡片"),
            noteId = 7L,
            sortOrder = 0
        )

        assertEquals(0L, entity.id)
    }

    @Test
    fun `accent 为 null 表示用户没改过 落库后仍为 null`() {
        val entity = CardMapper.toNewEntity(
            domain = KnowledgeCard(id = 0L, title = "卡片", accent = null),
            noteId = 7L,
            sortOrder = 0
        )
        assertEquals(null, entity.accent)
        assertEquals(null, CardMapper.toDomain(entity).accent)
    }

    @Test
    fun `用户显式选的身份色能往返`() {
        val custom = 0xFF4A7A7A.toInt()
        val entity = CardMapper.toNewEntity(
            domain = KnowledgeCard(id = 0L, title = "卡片", accent = custom),
            noteId = 7L,
            sortOrder = 1
        )
        assertEquals(custom, entity.accent)
        assertEquals(custom, CardMapper.toDomain(entity).accent)
    }
}