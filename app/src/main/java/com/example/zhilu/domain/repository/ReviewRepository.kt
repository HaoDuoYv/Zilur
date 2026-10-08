package com.example.zhilu.domain.repository

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.ReviewPlanWithNote
import com.example.zhilu.domain.model.ReviewRating
import kotlinx.coroutines.flow.Flow

interface ReviewRepository {
    /** 全部复习计划（含笔记标题、含暂停/已完成），复习中心列表用。 */
    fun observePlans(): Flow<RepositoryResult<List<ReviewPlanWithNote>>>

    /** 某时刻（今天 0 点）以来的复习次数，供「今日已复习 N 篇」。 */
    fun observeEventCountSince(since: Long): Flow<Int>
    suspend fun getPlanByNoteId(noteId: Long): RepositoryResult<ReviewPlan?>
    suspend fun startPlan(noteId: Long, now: Long): RepositoryResult<ReviewPlan>

    /**
     * 「继续」暂停中的计划：**保留 [ReviewPlan.currentStep]**，`nextReviewAt = now`
     * （立即回到待复习队列）。区别于 [startPlan] 的"重新开始"（档位归零）。
     */
    suspend fun enablePlan(noteId: Long, now: Long): RepositoryResult<ReviewPlan>
    suspend fun recordReview(
        plan: ReviewPlan,
        rating: ReviewRating,
        reviewedAt: Long
    ): RepositoryResult<ReviewPlan>
    suspend fun disablePlan(noteId: Long): RepositoryResult<Unit>
}
