package com.example.zhilu.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "review_events",
    indices = [Index(value = ["planId"]), Index(value = ["noteId"])]
)
data class ReviewEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val planId: Long,
    val noteId: Long,
    val reviewedAt: Long,
    val rating: Int,
    val previousStep: Int,
    val nextStep: Int,
    val nextReviewAt: Long?
)
