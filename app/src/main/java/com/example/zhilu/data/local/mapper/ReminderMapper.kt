package com.example.zhilu.data.local.mapper

import com.example.zhilu.data.local.entity.ReminderInstanceEntity
import com.example.zhilu.data.local.entity.ReminderWithContextRow
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderStatus
import com.example.zhilu.domain.model.ReminderType
import com.example.zhilu.domain.model.ReminderWithContext

object ReminderMapper {
    fun toDomain(entity: ReminderInstanceEntity): ReminderInstance = ReminderInstance(
        id = entity.id,
        type = ReminderType.fromValue(entity.type),
        sourceId = entity.sourceId,
        noteId = entity.noteId,
        dueAt = entity.dueAt,
        status = ReminderStatus.fromValue(entity.status),
        notificationId = entity.notificationId,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        firedAt = entity.firedAt
    )

    fun toDomainWithContext(row: ReminderWithContextRow): ReminderWithContext = ReminderWithContext(
        reminder = toDomain(row.reminder),
        noteTitle = row.noteTitle,
        todoContent = row.todoContent,
        reviewStep = row.reviewStep
    )

    fun toEntity(domain: ReminderInstance): ReminderInstanceEntity = ReminderInstanceEntity(
        id = domain.id,
        type = domain.type.value,
        sourceId = domain.sourceId,
        noteId = domain.noteId,
        dueAt = domain.dueAt,
        status = domain.status.value,
        notificationId = domain.notificationId,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt,
        firedAt = domain.firedAt
    )
}
