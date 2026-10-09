package com.example.zhilu.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.ReviewRating
import com.example.zhilu.domain.model.ReviewStats
import com.example.zhilu.ui.component.MetaLine
import com.example.zhilu.ui.component.SectionHeader
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 柱子的最大高度（峰值那天画满）；0 次的日子画一根 2dp 的浅色底痕，而不是什么都没有。 */
private val BarMaxHeight = 44.dp
private val BarMinHeight = 2.dp

/**
 * 复习统计：近一周曲线 + 评价分布 + 毕业率。
 *
 * 放在「待复习」档的队列上方 —— 这些数字回答的是"我复习得怎么样"，
 * 紧接着的队列回答"接下来复习什么"，两件事挨着看才顺。
 *
 * 颜色全部走 md3 语义角色（柱子用 `primary`、空柱用 `surfaceVariant`），
 * 圆角走 `MaterialTheme.shapes`，所以纸墨 / 动森 × 明暗四象限都自适应。
 */
@Composable
fun ReviewStatsSection(
    stats: ReviewStats,
    startOfToday: Long,
    graduatedPlanCount: Int,
    totalPlanCount: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader("复习统计")

        DailyBars(
            counts = stats.dailyCounts,
            labels = dailyAxisLabels(startOfToday),
            modifier = Modifier.padding(
                start = Spacing.PageGutter,
                end = Spacing.PageGutter,
                top = Spacing.Sm
            )
        )

        MetaLine(
            parts = buildList {
                add("近 7 天共 ${stats.windowTotal} 次")
                add("今天 ${stats.todayCount} 次")
                // 一个计划都没有时不写「毕业率 0%」—— 没有分母，那是句假话
                if (totalPlanCount > 0) {
                    add("毕业率 ${formatRatio(graduatedPlanCount, totalPlanCount)}（$graduatedPlanCount/$totalPlanCount 个计划）")
                }
            },
            modifier = Modifier.padding(
                start = Spacing.PageGutter,
                end = Spacing.PageGutter,
                top = Spacing.Sm
            )
        )

        MetaLine(
            parts = listOf(
                "困难 ${stats.countOf(ReviewRating.HARD)}",
                "正常 ${stats.countOf(ReviewRating.NORMAL)}",
                "已掌握 ${stats.countOf(ReviewRating.MASTERED)}"
            ),
            modifier = Modifier.padding(
                start = Spacing.PageGutter,
                end = Spacing.PageGutter,
                top = Spacing.Xs,
                bottom = Spacing.Sm
            )
        )
    }
}

/**
 * 毕业率文案：整数百分比。
 *
 * 不用小数点 —— 复习计划的数量是个位数，多一位是假精度。
 * 分母为 0 由调用方挡掉（不显示这一项）。
 */
internal fun formatRatio(graduated: Int, total: Int): String =
    if (total <= 0) "—" else "${(graduated * 100 + total / 2) / total}%"

/**
 * 近一周的横轴标签（最早 → 今天），用**日期**而不是星期几：
 * 「三」在一周里出现两遍会歧义，日期不会。
 *
 * 起点必须与数据窗口**同一份计算**（`ReviewStats.windowStart`）：
 * 第一版自己从 `startOfToday` 往后数 7 天，轴标就整个错位成了「今天 → 6 天后」
 * （真机表现：10 月 9 日看到轴上写着 9…15，且高亮的"今天"落在 15 上）。
 * 数字跨月会回绕（10-03 起首格是 27），这是预期行为。
 */
internal fun dailyAxisLabels(
    startOfToday: Long,
    days: Int = ReviewStats.WINDOW_DAYS
): List<String> {
    val firstDay = Instant.ofEpochMilli(ReviewStats.windowStart(startOfToday, days))
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return (0 until days).map { offset -> firstDay.plusDays(offset.toLong()).dayOfMonth.toString() }
}

/** 今天（最后一格）的日期数字，用来加粗标记。 */
internal fun todayAxisIndex(days: Int = ReviewStats.WINDOW_DAYS): Int = days - 1

@Composable
private fun DailyBars(
    counts: List<Int>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    // 全 0 时用 1 当分母，免得除零；柱子本来就都只有底痕高。
    val peak = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    val barColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val todayIndex = todayAxisIndex()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
        verticalAlignment = Alignment.Bottom
    ) {
        counts.forEachIndexed { index, count ->
            val isToday = index == todayIndex
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    // 0 次的日子不写数字，免得一排 0 盖过柱子本身
                    text = if (count > 0) count.toString() else "",
                    style = ZhiLuType.meta,
                    color = labelColor
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BarMaxHeight),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeight(count, peak))
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(if (count > 0) barColor else emptyColor)
                    )
                }
                Text(
                    text = labels.getOrElse(index) { "" },
                    style = ZhiLuType.meta,
                    color = if (isToday) barColor else labelColor,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** 柱子高度：按峰值等比，非 0 至少给 4dp（否则 1 次和 0 次看起来一样）。 */
private fun barHeight(count: Int, peak: Int): Dp {
    if (count <= 0) return BarMinHeight
    val ratio = count.toFloat() / peak
    val scaled = BarMaxHeight * ratio
    return if (scaled < BarMinVisible) BarMinVisible else scaled
}

private val BarMinVisible = 4.dp
