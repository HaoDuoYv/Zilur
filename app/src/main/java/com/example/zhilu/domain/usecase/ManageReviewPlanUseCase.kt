package com.example.zhilu.domain.usecase

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.reminder.ReviewReminderSync
import com.example.zhilu.domain.repository.ReviewRepository
import javax.inject.Inject

/**
 * 复习计划的三个状态动作（复习中心的 ⋮ 菜单），每个动作都把**计划**与**提醒**一起配平。
 *
 * 只调 `disablePlan`/`enablePlan` 而漏掉提醒同步，就会出现"计划停了、提醒照发"或
 * "继续了、却不提醒"的分叉；所以成对操作收口在这里，而不是散在 ViewModel 里。
 */
class ManageReviewPlanUseCase @Inject constructor(
    private val reviewRepository: ReviewRepository,
    private val reviewReminderSync: ReviewReminderSync
) {
    /**
     * 暂停：计划失活 + 取消 REVIEW 提醒。**保留档位**，之后可用 [resume] 原地继续，
     * 这与"重新开始"（[restart]，档位归零）是两回事。
     */
    suspend fun pause(plan: ReviewPlan): RepositoryResult<Unit> {
        val disabled = reviewRepository.disablePlan(plan.noteId)
        if (disabled is RepositoryResult.Error) return disabled
        return reviewReminderSync.sync(plan.copy(enabled = false, nextReviewAt = null))
    }

    /**
     * 继续：保留档位，`nextReviewAt = now` 立即回到待复习队列，并重建提醒。
     */
    suspend fun resume(
        plan: ReviewPlan,
        now: Long = System.currentTimeMillis()
    ): RepositoryResult<ReviewPlan> = when (val enabled = reviewRepository.enablePlan(plan.noteId, now)) {
        is RepositoryResult.Error -> enabled
        is RepositoryResult.Success -> {
            val synced = reviewReminderSync.sync(enabled.data)
            if (synced is RepositoryResult.Error) synced else enabled
        }
    }

    /**
     * 重新开始：档位归零（`startPlan` 的"重新开始"语义），并重建提醒。
     * 主要用于「已完成」的计划重新拾起。
     */
    suspend fun restart(
        noteId: Long,
        now: Long = System.currentTimeMillis()
    ): RepositoryResult<ReviewPlan> = when (val started = reviewRepository.startPlan(noteId, now)) {
        is RepositoryResult.Error -> started
        is RepositoryResult.Success -> {
            val synced = reviewReminderSync.sync(started.data)
            if (synced is RepositoryResult.Error) synced else started
        }
    }
}
