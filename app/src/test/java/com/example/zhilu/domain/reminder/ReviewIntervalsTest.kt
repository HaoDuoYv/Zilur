package com.example.zhilu.domain.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewIntervalsTest {

    // ---------- parse ----------

    @Test
    fun parsesEnglishCommas() {
        assertEquals(listOf(1L, 3L, 7L), ReviewIntervals.parse("1,3,7"))
    }

    @Test
    fun parsesChineseCommas() {
        assertEquals(listOf(1L, 3L), ReviewIntervals.parse("1，3"))
    }

    @Test
    fun parsesIdeographicCommas() {
        assertEquals(listOf(1L, 3L), ReviewIntervals.parse("1、3"))
    }

    @Test
    fun parsesWhitespaceSeparators() {
        assertEquals(listOf(1L, 3L, 7L), ReviewIntervals.parse("1 3\t7"))
        assertEquals(listOf(1L, 3L), ReviewIntervals.parse("1\n3"))
    }

    @Test
    fun trimsTokensAroundSeparators() {
        assertEquals(listOf(15L, 30L), ReviewIntervals.parse(" 15 , 30 "))
    }

    @Test
    fun mixedSeparatorsAreAccepted() {
        assertEquals(listOf(1L, 2L, 4L, 7L), ReviewIntervals.parse("1, 2、4，7"))
    }

    @Test
    fun nonNumericTokenFallsBackToNull() {
        assertNull(ReviewIntervals.parse("1,abc"))
        assertNull(ReviewIntervals.parse("1.5,3"))
    }

    @Test
    fun emptyOrSeparatorOnlyInputFallsBackToNull() {
        assertNull(ReviewIntervals.parse(""))
        assertNull(ReviewIntervals.parse("   "))
        assertNull(ReviewIntervals.parse(",,，、 "))
    }

    @Test
    fun zeroIsPassedThroughToValidation() {
        // 0 的"非正"判定归 validate，好在界面上给出更具体的文案。
        assertEquals(listOf(0L), ReviewIntervals.parse("0"))
    }

    // ---------- validate ----------

    @Test
    fun emptyListIsRejected() {
        assertEquals("至少需要一档", ReviewIntervals.validate(emptyList()))
    }

    @Test
    fun tooManyStepsAreRejected() {
        val days = (1..ReviewIntervals.MAX_STEPS + 1).map { it.toLong() }

        assertEquals("最多 ${ReviewIntervals.MAX_STEPS} 档", ReviewIntervals.validate(days))
    }

    @Test
    fun maxStepsBoundaryIsAccepted() {
        val days = (1..ReviewIntervals.MAX_STEPS).map { it.toLong() }

        assertNull(ReviewIntervals.validate(days))
    }

    @Test
    fun outOfRangeDayIsRejected() {
        val expected = "每档间隔需在 1–365 天之间"

        assertEquals(expected, ReviewIntervals.validate(listOf(0L)))
        assertEquals(expected, ReviewIntervals.validate(listOf(1L, 366L)))
    }

    @Test
    fun dayRangeBoundariesAreAccepted() {
        assertNull(ReviewIntervals.validate(listOf(1L, 365L)))
    }

    @Test
    fun equalOrDecreasingDaysAreRejected() {
        assertEquals("间隔需要从小到大排列", ReviewIntervals.validate(listOf(1L, 3L, 3L)))
        assertEquals("间隔需要从小到大排列", ReviewIntervals.validate(listOf(3L, 1L)))
    }

    @Test
    fun rangeErrorTakesPriorityOverOrderError() {
        // 顺序固定：先范围、后排列，一次只报最根本的问题。
        assertEquals("每档间隔需在 1–365 天之间", ReviewIntervals.validate(listOf(3L, 0L)))
    }

    @Test
    fun validLadderPasses() {
        assertNull(ReviewIntervals.validate(ReviewIntervals.DEFAULT))
    }

    @Test
    fun singleStepLadderIsValid() {
        assertNull(ReviewIntervals.validate(listOf(7L)))
    }

    // ---------- format / preview / summary ----------

    @Test
    fun formatJoinsWithAsciiCommas() {
        assertEquals("1,3,7,15,30", ReviewIntervals.format(ReviewIntervals.DEFAULT))
    }

    @Test
    fun previewReadsAsChainEndingWithGraduation() {
        assertEquals("1 天 → 3 天 → 毕业", ReviewIntervals.preview(listOf(1L, 3L)))
    }

    @Test
    fun summaryShowsDaysAndStepCount() {
        assertEquals("1 · 3 · 7 · 15 · 30 天（共 5 档）", ReviewIntervals.summary(ReviewIntervals.DEFAULT))
        assertEquals("7 天（共 1 档）", ReviewIntervals.summary(listOf(7L)))
    }

    @Test
    fun presetsAllPassValidation() {
        ReviewIntervals.PRESETS.forEach { preset ->
            assertNull("预设「${preset.label}」应合法", ReviewIntervals.validate(preset.days))
        }
    }
}
