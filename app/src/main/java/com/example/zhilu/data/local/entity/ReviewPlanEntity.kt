package com.example.zhilu.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "review_plans",
    indices = [Index(value = ["noteId"], unique = true)]
)
data class ReviewPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: Long,
    val enabled: Boolean,
    val currentStep: Int,
    val nextReviewAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?
)
