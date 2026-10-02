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
            isFocused = false,
            accent = entity.accent
        )

    fun toEntity(domain: KnowledgeCard, noteId: Long = 0, sortOrder: Int = 0): NoteCardEntity = NoteCardEntity(
        id = domain.id,
        noteId = noteId,
        title = domain.title,
        sortOrder = sortOrder,
        accent = domain.accent
    )

    /**
     * 写入用的卡片实体：**把 id 归零**，一律交给数据库分配主键。
     *
     * `KnowledgeCard.id` 在内存里只是临时值（新建的卡片为负数）。
     * 若原样落库，这些临时 id 会成为真实主键，与后续会话重新分配的临时 id 撞号，
     * 表现为卡片列表出现重复 key 而崩溃。与 `BlockMapper` 的处理方式保持一致。
     */
    fun toNewEntity(domain: KnowledgeCard, noteId: Long, sortOrder: Int): NoteCardEntity =
        toEntity(domain.copy(id = 0), noteId, sortOrder)
}
