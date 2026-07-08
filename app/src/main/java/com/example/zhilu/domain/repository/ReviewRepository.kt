package com.example.zhilu.domain.repository

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.ReviewRating

interface ReviewRepository {
    suspend fun getPlanByNoteId(noteId: Long): RepositoryResult<ReviewPlan?>
    suspend fun startPlan(noteId: Long, now: Long): RepositoryResult<ReviewPlan>
    suspend fun recordReview(
        plan: ReviewPlan,
        rating: ReviewRating,
        reviewedAt: Long
    ): RepositoryResult<ReviewPlan>
    suspend fun disablePlan(noteId: Long): RepositoryResult<Unit>
}
