package com.example.zhilu.data.repository

import com.example.zhilu.data.local.entity.ReviewDailyCountRow
import com.example.zhilu.data.local.entity.ReviewRatingCountRow
import com.example.zhilu.domain.model.ReviewRating
import com.example.zhilu.domain.model.ReviewStats
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 统计拼装：两条 `GROUP BY` 的结果要能安全地变成一排柱子。
 *
 * 这里是真机回归的护栏 —— `GROUP BY` 只返回**有事件的天**，
 * 直接拿去做柱状图会让柱子整体错位（周三的次数画到周一上）。
 */
class ReviewStatsBuildTest {

    @Test
    fun `missing days are filled with zero`() {
        val stats = buildReviewStats(
            daily = listOf(ReviewDailyCountRow(dayIndex = 3, eventCount = 2)),
            ratings = emptyList()
        )

        assertEquals(listOf(0, 0, 0, 2, 0, 0, 0), stats.dailyCounts)
        assertEquals(7, stats.dailyCounts.size)
    }

    @Test
    fun `bars keep their own day when several days have events`() {
        val stats = buildReviewStats(
            daily = listOf(
                ReviewDailyCountRow(dayIndex = 6, eventCount = 5),
                ReviewDailyCountRow(dayIndex = 1, eventCount = 2)
            ),
            ratings = emptyList()
        )

        assertEquals(2, stats.dailyCounts[1])
        assertEquals(5, stats.dailyCounts[6])
        assertEquals(0, stats.dailyCounts[3])
    }

    @Test
    fun `today is the last bar and the total is the whole week`() {
        val stats = buildReviewStats(
            daily = listOf(
                ReviewDailyCountRow(dayIndex = 0, eventCount = 1),
                ReviewDailyCountRow(dayIndex = 6, eventCount = 3)
            ),
            ratings = emptyList()
        )

        assertEquals(3, stats.todayCount)
        assertEquals(4, stats.windowTotal)
    }

    @Test
    fun `out of range days are dropped instead of crashing`() {
        // 时钟回拨会让事件落到窗口右侧之外；越界写入会抛 IndexOutOfBounds 并断掉整个统计流
        val stats = buildReviewStats(
            daily = listOf(
                ReviewDailyCountRow(dayIndex = 7, eventCount = 9),
                ReviewDailyCountRow(dayIndex = -1, eventCount = 9),
                ReviewDailyCountRow(dayIndex = 2, eventCount = 1)
            ),
            ratings = emptyList()
        )

        assertEquals(listOf(0, 0, 1, 0, 0, 0, 0), stats.dailyCounts)
    }

    @Test
    fun `rating counts land on their own buckets`() {
        val stats = buildReviewStats(
            daily = emptyList(),
            ratings = listOf(
                ReviewRatingCountRow(rating = ReviewRating.HARD.value, eventCount = 1),
                ReviewRatingCountRow(rating = ReviewRating.NORMAL.value, eventCount = 4),
                ReviewRatingCountRow(rating = ReviewRating.MASTERED.value, eventCount = 2)
            )
        )

        assertEquals(1, stats.countOf(ReviewRating.HARD))
        assertEquals(4, stats.countOf(ReviewRating.NORMAL))
        assertEquals(2, stats.countOf(ReviewRating.MASTERED))
    }

    @Test
    fun `empty window still yields a full week of bars`() {
        val stats = buildReviewStats(daily = emptyList(), ratings = emptyList())

        assertEquals(List(ReviewStats.WINDOW_DAYS) { 0 }, stats.dailyCounts)
        assertEquals(0, stats.windowTotal)
        assertEquals(0, stats.maxDailyCount)
    }
}
