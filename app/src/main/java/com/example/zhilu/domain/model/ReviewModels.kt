package com.example.zhilu.domain.model

data class ReviewPlan(
    val id: Long = 0,
    val noteId: Long,
    val enabled: Boolean = true,
    val currentStep: Int = 0,
    val nextReviewAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

data class ReviewEvent(
    val id: Long = 0,
    val planId: Long,
    val noteId: Long,
    val reviewedAt: Long,
    val rating: ReviewRating,
    val previousStep: Int,
    val nextStep: Int,
    val nextReviewAt: Long?
)

enum class ReviewRating(val value: Int) {
    HARD(1),
    NORMAL(2),
    MASTERED(3);

    companion object {
        fun fromValue(value: Int): ReviewRating =
            entries.firstOrNull { it.value == value } ?: NORMAL
    }
}

data class ReviewScheduleResult(
    val nextStep: Int,
    val nextReviewAt: Long?,
    val completed: Boolean
)
