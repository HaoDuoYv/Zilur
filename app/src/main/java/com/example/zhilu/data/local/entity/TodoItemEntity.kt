package com.example.zhilu.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "todo_items",
    indices = [Index(value = ["noteId"]), Index(value = ["remindAt"])]
)
data class TodoItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: Long?,
    val content: String,
    val remindAt: Long?,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val sortOrder: Int
)
