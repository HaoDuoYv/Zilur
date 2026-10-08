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

/** 复习计划 + 所属笔记标题（复习中心列表展示用）。 */
data class ReviewPlanWithNote(
    val plan: ReviewPlan,
    val noteTitle: String
)

/**
 * 「待复习」档的分区结果。
 *
 * [later] 单独成区（而不是并进 [upcoming]）是为了让 30 天档这类远期计划
 * 仍留在列表里可见——否则刚复习完一个长档位，列表里会"查无此计划"。
 */
data class ReviewQueue(
    val overdue: List<ReviewPlanWithNote> = emptyList(),
    val today: List<ReviewPlanWithNote> = emptyList(),
    val upcoming: List<ReviewPlanWithNote> = emptyList(),
    val later: List<ReviewPlanWithNote> = emptyList(),
    val paused: List<ReviewPlanWithNote> = emptyList(),
    val completed: List<ReviewPlanWithNote> = emptyList()
) {
    /** 队列中（未暂停、未毕业、且有下次复习时间）的计划数。 */
    val queuedCount: Int get() = overdue.size + today.size + upcoming.size + later.size

    /** 是否存在任何计划（含暂停 / 已完成），空状态判断用。 */
    val hasAnyPlan: Boolean get() = queuedCount > 0 || paused.isNotEmpty() || completed.isNotEmpty()
}
