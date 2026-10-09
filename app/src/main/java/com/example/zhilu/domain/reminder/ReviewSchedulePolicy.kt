package com.example.zhilu.domain.reminder

import com.example.zhilu.domain.model.ReviewRating
import com.example.zhilu.domain.model.ReviewScheduleResult

class ReviewSchedulePolicy(
    private val intervalsMillis: List<Long> = defaultIntervalsMillis
) {
    init {
        require(intervalsMillis.isNotEmpty()) { "intervalsMillis must not be empty" }
        require(intervalsMillis.all { it > 0 }) { "intervalsMillis values must be positive" }
    }

    /** 当前阶梯的档位总数（进度点个数 / 「第 N/M 次」里的 M）。 */
    val stepCount: Int get() = intervalsMillis.size

    fun start(now: Long): ReviewScheduleResult =
        ReviewScheduleResult(
            nextStep = 0,
            nextReviewAt = now + intervalsMillis[0],
            completed = false
        )

    fun advance(currentStep: Int, rating: ReviewRating, reviewedAt: Long): ReviewScheduleResult {
        require(currentStep >= 0) { "currentStep must be non-negative" }

        val targetStep = when (rating) {
            ReviewRating.HARD -> (currentStep - 1).coerceAtLeast(0)
            ReviewRating.NORMAL -> currentStep + 1
            ReviewRating.MASTERED -> currentStep + 2
        }
        val boundedStep = targetStep.coerceAtMost(intervalsMillis.lastIndex)
        val completed = targetStep > intervalsMillis.lastIndex
        val nextInterval = if (rating == ReviewRating.HARD) intervalsMillis[0] else intervalsMillis[boundedStep]
        return ReviewScheduleResult(
            nextStep = boundedStep,
            nextReviewAt = if (completed) null else reviewedAt + nextInterval,
            completed = completed
        )
    }

    companion object {
        private const val DAY = 86_400_000L

        /** 出厂阶梯（毫秒形态）—— 「天」的唯一来源是 [ReviewIntervals.DEFAULT]。 */
        val defaultIntervalsMillis: List<Long> = ReviewIntervals.DEFAULT.map { it * DAY }

        /** 从「天」构建（用户自定义阶梯的入口）。 */
        fun fromDays(days: List<Long>): ReviewSchedulePolicy =
            ReviewSchedulePolicy(days.map { it * DAY })

        /**
         * 默认阶梯的档位总数（`intervalsMillis.size`）。
         *
         * 用户改过阶梯后，界面上的「第 N/总次」与进度点个数应以**当前**阶梯为准
         * （读取方拿 `ReviewSchedulePolicy.stepCount` 或界面状态里的 stepCount）；
         * 这里只是"没读到自定义值"时的兜底，别再往界面里写死数字。
         */
        val defaultStepCount: Int = defaultIntervalsMillis.size
    }
}
