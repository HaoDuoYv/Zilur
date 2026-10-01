package com.example.zhilu.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_conversations",
    indices = [Index(value = ["updatedAt"])]
)
data class AiConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)
