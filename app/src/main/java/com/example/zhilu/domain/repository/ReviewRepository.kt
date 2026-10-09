package com.example.zhilu.domain.repository

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.ReviewHeatmap
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.ReviewPlanWithNote
import com.example.zhilu.domain.model.ReviewRating
import com.example.zhilu.domain.model.ReviewStats
import kotlinx.coroutines.flow.Flow

interface ReviewRepository {
    /** 全部复习计划（含笔记标题、含暂停/已完成），复习中心列表用。 */
    fun observePlans(): Flow<RepositoryResult<List<ReviewPlanWithNote>>>

    /**
     * 复习统计：近 [ReviewStats.WINDOW_DAYS] 天曲线 + 窗口内评价分布。
     *
     * 曲线的最后一格就是今天，所以「今天复习了几篇」取 `ReviewStats.todayCount`，
     * 不必再单独打一条今日计数 —— 同一张表、同一个口径的数字有两个来源，
     * 迟早会在跨零点时对不上。
     *
     * [windowStart] 由调用方按界面口径算（见 `ReviewStats.windowStart`），
     * 仓库只负责把两条聚合查询拼成一个对象、把缺席的天补成 0。
     */
    fun observeStats(windowStart: Long): Flow<ReviewStats>

    /**
     * 复习热力图：近 [ReviewHeatmap.WEEKS] 周按天分桶（列 = 周、行 = 星期）。
     *
     * [windowStart] 与 [todayIndex] 必须来自**同一次** [ReviewHeatmap.window] 计算：
     * 前者是 SQL 的分桶基准，后者决定界面把哪些格子画成「未来」，各自算一次
     * 就有跨零点错位的可能。仓库只负责补 0 与越界保护。
     */
    fun observeHeatmap(windowStart: Long, todayIndex: Int): Flow<ReviewHeatmap>

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
