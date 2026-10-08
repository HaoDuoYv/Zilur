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
        val defaultIntervalsMillis: List<Long> = listOf(1, 3, 7, 15, 30).map { it * DAY }

        /**
         * 默认阶梯的档位总数（`intervalsMillis.size`）。
         *
         * 界面上的「第 N/总次」与进度点个数都从这里取 —— 别在界面里写死数字，
         * 否则改阶梯长度时文案会悄悄说谎。
         */
        val defaultStepCount: Int = defaultIntervalsMillis.size
    }
}
