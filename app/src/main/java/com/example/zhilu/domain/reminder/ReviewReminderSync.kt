package com.example.zhilu.domain.reminder

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderType
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.repository.ReminderRepository
import javax.inject.Inject

/**
 * 复习计划 → REVIEW 提醒的**唯一同步点**。
 *
 * REVIEW 提醒的生死必须与 `review_plans.nextReviewAt` 一致（单一事实来源）：
 * 开启 / 评级 / 继续 / 重新开始后重建提醒，暂停或毕业（`nextReviewAt = null`）后取消提醒。
 * 写在 ViewModel 里各调一遍 `upsertScheduled`/`cancel` 迟早会漏配一处，
 * 于是会出现"计划还等着下次复习，但提醒已经没了"（或反过来）的分叉。
 */
class ReviewReminderSync @Inject constructor(
    private val reminderRepository: ReminderRepository
) {
    /**
     * 让 REVIEW 提醒与 [plan] 对齐：
     * - `nextReviewAt == null`（暂停 / 毕业）→ 取消提醒；
     * - 否则按 `nextReviewAt` upsert（upsert 会先取消同源旧实例，不会重复通知）。
     */
    suspend fun sync(plan: ReviewPlan): RepositoryResult<Unit> {
        val nextReviewAt = plan.nextReviewAt
        if (nextReviewAt == null) {
            return reminderRepository.cancel(ReminderType.REVIEW, plan.id)
        }
        val result = reminderRepository.upsertScheduled(
            ReminderInstance(
                type = ReminderType.REVIEW,
                sourceId = plan.id,
                noteId = plan.noteId,
                dueAt = nextReviewAt,
                notificationId = notificationIdFor(plan.id)
            )
        )
        return when (result) {
            is RepositoryResult.Success -> RepositoryResult.Success(Unit)
            is RepositoryResult.Error -> result
        }
    }

    companion object {
        /**
         * REVIEW 提醒的稳定通知 id。
         *
         * 由计划 id 派生而不是自增：同一计划的提醒被重建（评级 / 继续）后，
         * 通知仍然是同一身份，不会在通知栏留下第二条。
         */
        fun notificationIdFor(planId: Long): Int = "review-$planId".hashCode()
    }
}
