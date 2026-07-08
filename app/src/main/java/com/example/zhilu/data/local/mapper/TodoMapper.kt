package com.example.zhilu.data.local.mapper

import com.example.zhilu.data.local.entity.TodoItemEntity
import com.example.zhilu.domain.model.TodoItem

object TodoMapper {
    fun toDomain(entity: TodoItemEntity): TodoItem = TodoItem(
        id = entity.id,
        noteId = entity.noteId,
        content = entity.content,
        remindAt = entity.remindAt,
        completedAt = entity.completedAt,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        sortOrder = entity.sortOrder
    )

    fun toEntity(domain: TodoItem): TodoItemEntity = TodoItemEntity(
        id = domain.id,
        noteId = domain.noteId,
        content = domain.content,
        remindAt = domain.remindAt,
        completedAt = domain.completedAt,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt,
        sortOrder = domain.sortOrder
    )
}
