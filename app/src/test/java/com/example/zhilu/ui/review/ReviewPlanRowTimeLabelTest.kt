package com.example.zhilu.ui.review

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 行内时间文案的天粒度口径。
 *
 * 这里是真机回归的护栏：`daysUntil` 曾按小时**向上取整**，于是「今天下午开启 1 天间隔、
 * 明天下午到期」被读成 2 天 → 明明明天到期却写「后天」。
 */
class ReviewPlanRowTimeLabelTest {
    private val startOfToday = 1_700_000_000_000L
    private val day = 86_400_000L
    private val hour = 3_600_000L

    @Test
    fun `due tomorrow afternoon reads as tomorrow`() {
        val due = startOfToday + day + 16 * hour

        assertEquals(1L, daysUntil(due, startOfToday))
        assertEquals("明天", zoneTimeLabel(ReviewPlanZone.Upcoming, due, startOfToday))
    }

    @Test
    fun `due exactly at tomorrow midnight reads as tomorrow`() {
        val due = startOfToday + day

        assertEquals(1L, daysUntil(due, startOfToday))
        assertEquals("明天", zoneTimeLabel(ReviewPlanZone.Upcoming, due, startOfToday))
    }

    @Test
    fun `due the day after tomorrow reads as the day after`() {
        val due = startOfToday + 2 * day + 5 * hour

        assertEquals(2L, daysUntil(due, startOfToday))
        assertEquals("后天", zoneTimeLabel(ReviewPlanZone.Upcoming, due, startOfToday))
    }

    @Test
    fun `long interval reads as a day count`() {
        val due = startOfToday + 7 * day

        assertEquals(7L, daysUntil(due, startOfToday))
        assertEquals("7 天后", zoneTimeLabel(ReviewPlanZone.Later, due, startOfToday))
    }

    @Test
    fun `overdue keeps at least one day`() {
        val justMissed = startOfToday - hour

        assertEquals(1L, overdueDays(justMissed, startOfToday))
        assertEquals("逾期 1 天", zoneTimeLabel(ReviewPlanZone.Overdue, justMissed, startOfToday))
    }

    @Test
    fun `overdue across several days counts round up`() {
        val due = startOfToday - 3 * day

        assertEquals(3L, overdueDays(due, startOfToday))
        assertEquals("逾期 3 天", zoneTimeLabel(ReviewPlanZone.Overdue, due, startOfToday))
    }

    @Test
    fun `archived and today zones use state words`() {
        assertEquals("今天", zoneTimeLabel(ReviewPlanZone.Today, startOfToday + hour, startOfToday))
        assertEquals("已暂停", zoneTimeLabel(ReviewPlanZone.Paused, null, startOfToday))
        assertEquals("已完成", zoneTimeLabel(ReviewPlanZone.Completed, null, startOfToday))
    }

    @Test
    fun `missing due date degrades safely`() {
        assertEquals(0L, daysUntil(null, startOfToday))
        assertEquals(1L, overdueDays(null, startOfToday))
    }
}
