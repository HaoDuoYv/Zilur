package com.example.zhilu.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.ReviewRating
import com.example.zhilu.domain.model.ReviewStats
import com.example.zhilu.ui.component.AppCard
import com.example.zhilu.ui.component.MetaLine
import com.example.zhilu.ui.theme.LocalExtendedColors
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 复习概览 2×2 统计格 + 评价分布小字行（对齐产品原型的 `stats-grid`）。
 *
 * - 「近 7 天 / 今日」来自 [stats]（窗口最后一格就是今天，今日不另设查询）；
 * - 「已掌握 / 毕业率」来自计划队列（[graduatedPlanCount] / [totalPlanCount]）——
 *   与队列的分区同一份数据，不再打聚合查询；
 * - 评价分布（困难 / 正常 / 已掌握次数）压缩成一行小字，不再占一整块 ——
 *   三格数字回答的是"复习得怎么样"，不值得和三格并列抢视线。
 */
@Composable
fun ReviewStatsGrid(
    stats: ReviewStats,
    graduatedPlanCount: Int,
    totalPlanCount: Int,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.CardGap)) {
            StatCell(
                label = "近 7 天复习",
                value = stats.windowTotal.toString(),
                unit = "次"
            )
            StatCell(
                label = "今日已复习",
                value = stats.todayCount.toString(),
                unit = "次",
                valueColor = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(Spacing.CardGap))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.CardGap)) {
            StatCell(
                label = "已掌握",
                value = graduatedPlanCount.toString(),
                unit = "个"
            )
            StatCell(
                label = "毕业率",
                // 没有分母时不写「0%」—— 那是句假话（与统计区老版口径一致）
                value = if (totalPlanCount > 0) {
                    formatRatio(graduatedPlanCount, totalPlanCount).removeSuffix("%")
                } else {
                    "—"
                },
                unit = if (totalPlanCount > 0) "%" else ""
            )
        }
        Spacer(modifier = Modifier.height(Spacing.Sm))
        MetaLine(
            parts = listOf(
                "近 7 天：困难 ${stats.countOf(ReviewRating.HARD)}",
                "正常 ${stats.countOf(ReviewRating.NORMAL)}",
                "已掌握 ${stats.countOf(ReviewRating.MASTERED)}"
            ),
            color = LocalExtendedColors.current.inkFaint
        )
    }
}

/** 单格：小标签在上、大数字在下（数字后的单位小一号）。 */
@Composable
private fun RowScope.StatCell(
    label: String,
    value: String,
    unit: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = label,
            style = ZhiLuType.label,
            color = LocalExtendedColors.current.inkFaint
        )
        Row(
            modifier = Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = value,
                style = ZhiLuType.cardTitle,
                color = valueColor
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    style = ZhiLuType.meta,
                    color = LocalExtendedColors.current.inkFaint,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                )
            }
        }
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
