package com.example.zhilu.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.ReviewPlanWithNote
import com.example.zhilu.ui.component.DocumentRow
import com.example.zhilu.ui.component.MetaLine
import com.example.zhilu.ui.component.QuietAction
import com.example.zhilu.ui.theme.ShapeTokens
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/** 计划所处的分区（决定书脊色、行内时间文案与可用操作）。 */
enum class ReviewPlanZone {
    Overdue, Today, Upcoming, Later, Paused, Completed
}

private val PillShape = RoundedCornerShape(ShapeTokens.Pill)

/**
 * 复习计划行：左侧状态色书脊 + 笔记标题 + 阶梯进度点 + 到期描述 + 主操作。
 *
 * 行壳沿用 [DocumentRow]（与笔记/提醒列表同一套结构）：无卡片、无背景，
 * 书脊色走 md3 语义色，两套外观（纸墨 / 动森）下都自适应。
 *
 * @param stepTotal 阶梯总档数（进度点的圆点数，由调用方从 `ReviewSchedulePolicy` 取）。
 * @param startOfToday 今天 0 点，「逾期 N 天 / N 天后」按它计算（天粒度）。
 */
@Composable
fun ReviewPlanRow(
    item: ReviewPlanWithNote,
    zone: ReviewPlanZone,
    stepTotal: Int,
    startOfToday: Long,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val plan = item.plan
    val accent = zoneAccent(zone)

    DocumentRow(
        accent = accent,
        modifier = modifier,
        onClick = onOpen
    ) {
        Text(
            text = item.noteTitle,
            style = ZhiLuType.rowTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepDots(
                currentStep = plan.currentStep,
                total = stepTotal,
                activeColor = accent
            )
            MetaLine(
                parts = listOf(
                    "第 ${plan.currentStep.coerceAtMost(stepTotal - 1) + 1}/$stepTotal 次",
                    zoneTimeLabel(zone, plan.nextReviewAt, startOfToday)
                ),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (zone) {
                ReviewPlanZone.Overdue, ReviewPlanZone.Today -> {
                    OutlinedButton(onClick = onStart, shape = PillShape) {
                        Text("开始复习", style = ZhiLuType.chip)
                    }
                    QuietAction(label = "暂停", onClick = onPause)
                }
                ReviewPlanZone.Paused -> {
                    OutlinedButton(onClick = onResume, shape = PillShape) {
                        Text("继续", style = ZhiLuType.chip)
                    }
                    QuietAction(label = "重新开始", onClick = onRestart)
                }
                ReviewPlanZone.Completed -> {
                    OutlinedButton(onClick = onRestart, shape = PillShape) {
                        Text("重新开始", style = ZhiLuType.chip)
                    }
                }
                // 未到期（接下来 / 之后）：仅展示，点整行进笔记看内容。
                ReviewPlanZone.Upcoming, ReviewPlanZone.Later -> Unit
            }
        }
    }
}

/**
 * 阶梯进度点：`currentStep` 及其之前为实心。
 *
 * 让"复习到哪一步 / 一共几步"一眼可见 —— 这是复习中心相对笔记内面板新增的信息。
 */
@Composable
private fun StepDots(currentStep: Int, total: Int, activeColor: Color) {
    val inactive = MaterialTheme.colorScheme.surfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .size(DotSize)
                    .clip(CircleShape)
                    .background(if (index <= currentStep) activeColor else inactive)
            )
        }
    }
}

private val DotSize = 6.dp

/** 分区 → 书脊色（全部 md3 语义色，跟随明暗与两套外观）。 */
@Composable
private fun zoneAccent(zone: ReviewPlanZone): Color = when (zone) {
    ReviewPlanZone.Overdue -> MaterialTheme.colorScheme.error
    ReviewPlanZone.Today -> MaterialTheme.colorScheme.primary
    ReviewPlanZone.Upcoming, ReviewPlanZone.Later -> MaterialTheme.colorScheme.tertiary
    ReviewPlanZone.Paused -> MaterialTheme.colorScheme.outline
    ReviewPlanZone.Completed -> MaterialTheme.colorScheme.tertiary
}

/**
 * 行内时间描述（天粒度，与分类器同口径）。
 *
 * @param nextReviewAt 已暂停 / 已完成时为 null，文案退化为状态词。
 */
internal fun zoneTimeLabel(zone: ReviewPlanZone, nextReviewAt: Long?, startOfToday: Long): String =
    when (zone) {
        ReviewPlanZone.Overdue -> {
            val days = overdueDays(nextReviewAt, startOfToday)
            if (days > 1) "逾期 $days 天" else "逾期 1 天"
        }
        ReviewPlanZone.Today -> "今天"
        ReviewPlanZone.Upcoming, ReviewPlanZone.Later -> when (val days = daysUntil(nextReviewAt, startOfToday)) {
            1L -> "明天"
            2L -> "后天"
            else -> "$days 天后"
        }
        ReviewPlanZone.Paused -> "已暂停"
        ReviewPlanZone.Completed -> "已完成"
    }

/** 逾期天数（向上取整，至少 1 天；`nextReviewAt` 为空时按 1 天兜底）。 */
internal fun overdueDays(nextReviewAt: Long?, startOfToday: Long): Long {
    val due = nextReviewAt ?: return 1L
    return (((startOfToday - due) + DAY - 1) / DAY).coerceAtLeast(1L)
}

/**
 * 距到期天数，按**自然日差**（今天 0 点起算）而非按小时向上取整。
 *
 * 向上取整会把「明天 16:00 到期」（间隔 1 天、下午开启的计划）读成 2 天 → 显示「后天」——
 * 真机上就是这么错的。`upcoming`／`later` 区的到期时间必定 ≥ 明天 0 点，
 * 所以整除结果最小就是 1，不会掉出「明天」这个档。
 */
internal fun daysUntil(nextReviewAt: Long?, startOfToday: Long): Long {
    val due = nextReviewAt ?: return 0L
    return (due - startOfToday) / DAY
}

private const val DAY = 86_400_000L
