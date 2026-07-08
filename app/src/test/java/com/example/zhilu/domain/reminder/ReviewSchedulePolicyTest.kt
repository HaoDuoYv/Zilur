package com.example.zhilu.domain.reminder

import com.example.zhilu.domain.model.ReviewRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSchedulePolicyTest {
    private val now = 1_000_000L
    private val day = 86_400_000L
    private val policy = ReviewSchedulePolicy()

    @Test
    fun firstReviewStartsAtOneDay() {
        val result = policy.start(now)

        assertEquals(0, result.nextStep)
        assertEquals(now + day, result.nextReviewAt)
        assertFalse(result.completed)
    }

    @Test
    fun hardRepeatsSoonWithoutAdvancingStep() {
        val result = policy.advance(currentStep = 2, rating = ReviewRating.HARD, reviewedAt = now)

        assertEquals(1, result.nextStep)
        assertEquals(now + day, result.nextReviewAt)
        assertFalse(result.completed)
    }

    @Test
    fun normalAdvancesOneStep() {
        val result = policy.advance(currentStep = 1, rating = ReviewRating.NORMAL, reviewedAt = now)

        assertEquals(2, result.nextStep)
        assertEquals(now + 7 * day, result.nextReviewAt)
        assertFalse(result.completed)
    }

    @Test
    fun masteredAdvancesFaster() {
        val result = policy.advance(currentStep = 1, rating = ReviewRating.MASTERED, reviewedAt = now)

        assertEquals(3, result.nextStep)
        assertEquals(now + 15 * day, result.nextReviewAt)
        assertFalse(result.completed)
    }

    @Test
    fun finalStepCompletesPlan() {
        val result = policy.advance(currentStep = 4, rating = ReviewRating.MASTERED, reviewedAt = now)

        assertEquals(4, result.nextStep)
        assertEquals(null, result.nextReviewAt)
        assertTrue(result.completed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyIntervalsAreRejected() {
        ReviewSchedulePolicy(intervalsMillis = emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveIntervalsAreRejected() {
        ReviewSchedulePolicy(intervalsMillis = listOf(day, 0L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeCurrentStepIsRejected() {
        policy.advance(currentStep = -1, rating = ReviewRating.NORMAL, reviewedAt = now)
    }

    @Test
    fun normalAtFinalStepCompletesPlan() {
        val result = policy.advance(currentStep = 4, rating = ReviewRating.NORMAL, reviewedAt = now)

        assertEquals(4, result.nextStep)
        assertEquals(null, result.nextReviewAt)
        assertTrue(result.completed)
    }

    @Test
    fun masteredFromPenultimateStepCompletesPlan() {
        val result = policy.advance(currentStep = 3, rating = ReviewRating.MASTERED, reviewedAt = now)

        assertEquals(4, result.nextStep)
        assertEquals(null, result.nextReviewAt)
        assertTrue(result.completed)
    }
}
