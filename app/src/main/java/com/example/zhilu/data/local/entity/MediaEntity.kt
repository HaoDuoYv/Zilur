package com.example.zhilu.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media",
    indices = [Index(value = ["uri"], unique = true)]
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uri: String,
    val width: Int?,
    val height: Int?,
    val size: Long,
    val createdAt: Long
)
