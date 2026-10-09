package com.example.zhilu.ui.review

import com.example.zhilu.domain.model.ReviewHeatmap
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 复习热力图的纯函数：网格窗口、今天格位、日期与月份轴标。
 *
 * 这里延续复习统计区那笔旧账的护栏：「轴标 / 格位与数据分桶必须共用同一份窗口计算」。
 * 热力图比曲线多一层约束 —— **列 = 周、行 = 星期**，今天必须落在自己星期几的那一行上；
 * 若按「今天往前 105 天」取窗口，今天会漂到任意一行，行标（一 / 三 / 五 / 日）就是假话。
 */
class ReviewHeatmapTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun startOfTodayAt(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    private fun gridStartDate(windowStart: Long): LocalDate =
        Instant.ofEpochMilli(windowStart).atZone(zone).toLocalDate()

    // ---- 网格窗口 ----

    @Test
    fun `grid starts on a monday fourteen weeks before the current week`() {
        // 2026-10-09 是周五 → 本周一 10-05，网格起点 = 往前 14 周 = 06-29
        val window = ReviewHeatmap.window(startOfTodayAt(2026, 10, 9), zone)

        val start = gridStartDate(window.start)
        assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
        assertEquals(LocalDate.of(2026, 6, 29), start)
    }

    @Test
    fun `today lands on the row of its own weekday`() {
        // 周五（10-09）应落在最后一周的第 5 行：98 + (5 - 1)
        val friday = ReviewHeatmap.window(startOfTodayAt(2026, 10, 9), zone)
        assertEquals(98 + 4, friday.todayIndex)
        assertEquals(4, friday.todayIndex % 7)

        // 周日（10-11）落在第 7 行（0 起 6），且仍是同一周、同一网格起点
        val sunday = ReviewHeatmap.window(startOfTodayAt(2026, 10, 11), zone)
        assertEquals(104, sunday.todayIndex)
        assertEquals(6, sunday.todayIndex % 7)
        assertEquals(friday.start, sunday.start)
    }

    @Test
    fun `today index always sits inside the last week of the grid`() {
        // 任意一天：今天格位都必须在最后一列（第 15 周）
        for (offset in 0L until 15L) {
            val window = ReviewHeatmap.window(startOfTodayAt(2026, 10, 5) + offset * 86_400_000L, zone)
            val lastWeekStart = ReviewHeatmap.DAYS - 7
            assertEquals(true, window.todayIndex >= lastWeekStart)
            assertEquals(true, window.todayIndex < ReviewHeatmap.DAYS)
        }
    }

    // ---- 五档分桶 ----

    @Test
    fun `level buckets map counts to five intensity steps`() {
        assertEquals(0, ReviewHeatmap.levelOf(0))
        assertEquals(1, ReviewHeatmap.levelOf(1))
        assertEquals(2, ReviewHeatmap.levelOf(2))
        assertEquals(2, ReviewHeatmap.levelOf(3))
        assertEquals(3, ReviewHeatmap.levelOf(4))
        assertEquals(3, ReviewHeatmap.levelOf(6))
        assertEquals(4, ReviewHeatmap.levelOf(7))
        assertEquals(4, ReviewHeatmap.levelOf(20))
    }

    @Test
    fun `level at reads through the grid safely`() {
        val heatmap = ReviewHeatmap(counts = List(ReviewHeatmap.DAYS) { if (it == 30) 5 else 0 })

        assertEquals(3, heatmap.levelAt(30))
        assertEquals(0, heatmap.levelAt(0))
        // 越界读数不崩（防御时钟产物）
        assertEquals(0, heatmap.levelAt(999))
    }

    // ---- 日期与轴标（与窗口同源）----

    @Test
    fun `cell date advances one natural day per index`() {
        val start = startOfTodayAt(2026, 6, 29)

        assertEquals("6月29日", heatmapCellDate(start, 0, zone))
        // 6-29 起第 102 天 = 10-09（1+31+31+30 = 93 天到 9-30，再 9 天到 10-09）
        assertEquals("10月9日", heatmapCellDate(start, 102, zone))
    }

    @Test
    fun `month labels sample the same window as the grid`() {
        // 采样 0 / 52 / 104 天：6-29 → 8-20 → 10-11
        val labels = heatmapMonthLabels(startOfTodayAt(2026, 6, 29), zone)

        assertEquals(listOf("6月", "8月", "10月"), labels)
    }

    @Test
    fun `month labels always cover three distinct months in a fifteen week window`() {
        // 相邻采样点相隔 52 天，大于最长月份的天数跨度 —— 三个标签必为三个不同的月
        val labels = heatmapMonthLabels(startOfTodayAt(2026, 1, 5), zone)

        assertEquals(3, labels.size)
        assertEquals(labels.distinct(), labels)
    }
}
