package com.example.zhilu.domain.reminder

import com.example.zhilu.domain.model.ReviewPlanWithNote
import com.example.zhilu.domain.model.ReviewQueue

/**
 * 把复习计划按「该不该现在复习」分区（纯函数，便于单测）。
 *
 * 口径以**今天 0 点**为界，而不是 `now`——复习是"天"粒度，
 * 用 `now` 切会让同一天内的计划在逾期/今天之间跳动。
 *
 * - `overdue`：`nextReviewAt < 今天 0 点`
 * - `today`：今天 0 点 ≤ `nextReviewAt` < 明天 0 点
 * - `upcoming`：明天 0 点起 7 天内
 * - `later`：7 天以后（30 天档复习完落在这里，保证"计划还在"）
 * - `paused` / `completed`：`enabled = false` 的计划（毕业看 `completedAt` 区分）
 */
class ReviewQueueClassifier {
    fun classify(plans: List<ReviewPlanWithNote>, startOfToday: Long): ReviewQueue {
        val endOfToday = startOfToday + DAY
        val endOfUpcoming = startOfToday + UPCOMING_DAYS * DAY

        val queued: List<Pair<ReviewPlanWithNote, Long>> = plans.mapNotNull { item ->
            val nextReviewAt = item.plan.nextReviewAt
            if (item.plan.enabled && nextReviewAt != null) item to nextReviewAt else null
        }
        val archived = plans.filterNot { it.plan.enabled }

        return ReviewQueue(
            overdue = queued.filter { it.second < startOfToday }
                .sortedBy { it.second }.map { it.first },
            today = queued.filter { it.second in startOfToday until endOfToday }
                .sortedBy { it.second }.map { it.first },
            upcoming = queued.filter { it.second in endOfToday until endOfUpcoming }
                .sortedBy { it.second }.map { it.first },
            later = queued.filter { it.second >= endOfUpcoming }
                .sortedBy { it.second }.map { it.first },
            paused = archived.filter { it.plan.completedAt == null }
                .sortedByDescending { it.plan.updatedAt },
            completed = archived.filter { it.plan.completedAt != null }
                .sortedByDescending { it.plan.updatedAt }
        )
    }

    companion object {
        /** 「接下来」区的窗口：7 天。 */
        const val UPCOMING_DAYS = 7
        private const val DAY = 86_400_000L
    }
}
