package com.example.zhilu.ui.review

import com.example.zhilu.domain.model.ReviewStats
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 复习统计区的纯函数：轴标、今日索引、毕业率文案。
 *
 * 轴标单独拎出来钉，是因为它曾与数据窗口错位（真机 bug：10 月 9 日打开复习中心，
 * 横轴写着 9…15、高亮的"今天"落在 15 上）—— 轴标不参与分桶、分桶不画轴，
 * `ReviewStatsBuildTest` 只管分桶，错位就一直没人管。现在轴标与分桶共用
 * `ReviewStats.windowStart`，本类钉住这条不变式。
 */
class ReviewStatsSectionTest {

    private fun startOfTodayAt(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    // ---- 轴标 ----

    @Test
    fun `last label is today`() {
        val labels = dailyAxisLabels(startOfTodayAt(2026, 10, 9))
        assertEquals("9", labels.last())
    }

    @Test
    fun `first label is six days before today`() {
        val labels = dailyAxisLabels(startOfTodayAt(2026, 10, 9))
        assertEquals(ReviewStats.WINDOW_DAYS, labels.size)
        assertEquals("3", labels.first())
    }

    @Test
    fun `labels read forward from the window start`() {
        val labels = dailyAxisLabels(startOfTodayAt(2026, 10, 9))
        assertEquals(listOf("3", "4", "5", "6", "7", "8", "9"), labels)
    }

    @Test
    fun `labels roll over month boundaries`() {
        // 窗口 = 09-27 … 10-03
        val labels = dailyAxisLabels(startOfTodayAt(2026, 10, 3))
        assertEquals(listOf("27", "28", "29", "30", "1", "2", "3"), labels)
    }

    @Test
    fun `first label is the same day as the stats window start`() {
        // 不变式：轴标起点与数据分桶起点必须是同一天（真机上两者错位过一次）
        val startOfToday = startOfTodayAt(2026, 10, 9)
        val windowStartDay = Instant.ofEpochMilli(ReviewStats.windowStart(startOfToday))
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .dayOfMonth
            .toString()
        assertEquals(windowStartDay, dailyAxisLabels(startOfToday).first())
    }

    @Test
    fun `today index points at the last cell`() {
        assertEquals(ReviewStats.WINDOW_DAYS - 1, todayAxisIndex())
    }

    // ---- 毕业率文案 ----

    @Test
    fun `ratio rounds to the nearest whole percent`() {
        assertEquals("33%", formatRatio(1, 3))
        assertEquals("67%", formatRatio(2, 3))
        assertEquals("50%", formatRatio(1, 2))
    }

    @Test
    fun `nothing graduated is a real zero`() {
        assertEquals("0%", formatRatio(0, 1))
    }

    @Test
    fun `no plans at all gets a dash rather than a fake zero`() {
        // 没有分母时「0%」是句假话（调用方本就不该显示这一项，这是兜底）
        assertEquals("—", formatRatio(0, 0))
    }
}
