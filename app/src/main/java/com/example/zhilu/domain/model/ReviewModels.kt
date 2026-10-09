package com.example.zhilu.domain.model

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ReviewPlan(
    val id: Long = 0,
    val noteId: Long,
    val enabled: Boolean = true,
    val currentStep: Int = 0,
    val nextReviewAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

data class ReviewEvent(
    val id: Long = 0,
    val planId: Long,
    val noteId: Long,
    val reviewedAt: Long,
    val rating: ReviewRating,
    val previousStep: Int,
    val nextStep: Int,
    val nextReviewAt: Long?
)

enum class ReviewRating(val value: Int) {
    HARD(1),
    NORMAL(2),
    MASTERED(3);

    companion object {
        fun fromValue(value: Int): ReviewRating =
            entries.firstOrNull { it.value == value } ?: NORMAL
    }
}

data class ReviewScheduleResult(
    val nextStep: Int,
    val nextReviewAt: Long?,
    val completed: Boolean
)

/** 复习计划 + 所属笔记标题（复习中心列表展示用）。 */
data class ReviewPlanWithNote(
    val plan: ReviewPlan,
    val noteTitle: String
)

/**
 * 复习统计（复习中心「待复习」档顶部的概览）。
 *
 * 窗口口径与列表一致：**今天 0 点**往前推满 [WINDOW_DAYS] 天（今天落在最后一格），
 * 而不是"最近 168 小时" —— 否则同一个数字会在午夜前后跳一下。
 */
data class ReviewStats(
    val dailyCounts: List<Int> = List(WINDOW_DAYS) { 0 },
    val ratingCounts: Map<ReviewRating, Int> = emptyMap()
) {
    /** 窗口内合计。 */
    val windowTotal: Int get() = dailyCounts.sum()

    /** 今天（窗口最后一格）的复习次数。 */
    val todayCount: Int get() = dailyCounts.lastOrNull() ?: 0

    /** 峰值，画柱状图时用来定比例（全 0 时调用方应自行兜底为 1）。 */
    val maxDailyCount: Int get() = dailyCounts.maxOrNull() ?: 0

    fun countOf(rating: ReviewRating): Int = ratingCounts[rating] ?: 0

    companion object {
        /** 曲线格子数：一周，最右一格是今天。 */
        const val WINDOW_DAYS = 7

        private const val DAY_MILLIS = 86_400_000L

        /** 窗口起点：今天 0 点往前推满窗口，使最后一格正好是今天。 */
        fun windowStart(startOfToday: Long, days: Int = WINDOW_DAYS): Long =
            startOfToday - (days - 1) * DAY_MILLIS
    }
}

/**
 * [ReviewHeatmap.window] 的计算结果：网格起点毫秒 + 今天的格位。
 *
 * 成对生成、成对使用 —— 查询窗口与「哪一格是今天」若分开算第二次，
 * 就可能跨零点算出互相对不上的一对。
 */
data class HeatmapWindow(val start: Long, val todayIndex: Int)

/**
 * 复习热力图（GitHub 风格网格）：[WEEKS] 列 × 7 行，**列 = 周、行 = 星期**。
 *
 * 网格起点是「本周一」往前推满 [WEEKS] 周，所以最后一列永远是本周。
 * 不按「今天往前 105 天」取窗口 —— 那样今天会落在任意一行，跟行标
 * （一 / 三 / 五 / 日）对不上，同一列里还会混进两个星期。
 */
data class ReviewHeatmap(
    /** [DAYS] 个格子的复习次数，**列优先**：第 col 列第 row 行 = `counts[col * 7 + row]`。 */
    val counts: List<Int> = List(DAYS) { 0 },
    /** 今天在网格中的格位（0 起）：之前是历史，之后是未来（界面画成空格）。 */
    val todayIndex: Int = counts.lastIndex,
    /** 网格起点（第 0 格的 0 点毫秒），界面算日期文案与月份轴标都用它 —— 不许自己再推一遍。 */
    val windowStart: Long = 0L
) {
    /** 单格强度（0..4 五档），界面据此取色。 */
    fun levelAt(index: Int): Int = levelOf(counts.getOrElse(index) { 0 })

    companion object {
        const val WEEKS = 15
        const val DAYS = WEEKS * 7

        /** 五档分桶：0 次 / 1 次 / 2–3 次 / 4–6 次 / 7 次以上。 */
        fun levelOf(count: Int): Int = when {
            count <= 0 -> 0
            count == 1 -> 1
            count <= 3 -> 2
            count <= 6 -> 3
            else -> 4
        }

        /**
         * 网格窗口：起点 + 今天格位**一次算好**。
         *
         * 数据查询的 windowStart、界面月份轴标、区分历史/未来的今天格位，
         * 都只能从这一对里取（同「轴标与分桶共用窗口起点」的教训）。
         */
        fun window(startOfToday: Long, zone: ZoneId): HeatmapWindow {
            val today = Instant.ofEpochMilli(startOfToday).atZone(zone).toLocalDate()
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val gridStart = monday.minusWeeks((WEEKS - 1).toLong())
            return HeatmapWindow(
                start = gridStart.atStartOfDay(zone).toInstant().toEpochMilli(),
                todayIndex = ChronoUnit.DAYS.between(gridStart, today).toInt()
            )
        }
    }
}

/**
 * 「待复习」档的分区结果。
 *
 * [later] 单独成区（而不是并进 [upcoming]）是为了让 30 天档这类远期计划
 * 仍留在列表里可见——否则刚复习完一个长档位，列表里会"查无此计划"。
 */
data class ReviewQueue(
    val overdue: List<ReviewPlanWithNote> = emptyList(),
    val today: List<ReviewPlanWithNote> = emptyList(),
    val upcoming: List<ReviewPlanWithNote> = emptyList(),
    val later: List<ReviewPlanWithNote> = emptyList(),
    val paused: List<ReviewPlanWithNote> = emptyList(),
    val completed: List<ReviewPlanWithNote> = emptyList()
) {
    /** 队列中（未暂停、未毕业、且有下次复习时间）的计划数。 */
    val queuedCount: Int get() = overdue.size + today.size + upcoming.size + later.size

    /** 是否存在任何计划（含暂停 / 已完成），空状态判断用。 */
    val hasAnyPlan: Boolean get() = queuedCount > 0 || paused.isNotEmpty() || completed.isNotEmpty()
}
