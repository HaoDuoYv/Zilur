package com.example.zhilu.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminder_instances",
    indices = [
        Index(value = ["type", "sourceId", "status"]),
        Index(value = ["noteId"]),
        Index(value = ["dueAt"])
    ]
)
data class ReminderInstanceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: Int,
    val sourceId: Long,
    val noteId: Long?,
    val dueAt: Long,
    val status: Int,
    val notificationId: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val firedAt: Long?
)
