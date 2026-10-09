package com.example.zhilu.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.ReviewPlanWithNote
import com.example.zhilu.ui.component.AppCard
import com.example.zhilu.ui.component.QuietAction
import com.example.zhilu.ui.theme.ShapeTokens
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/** 计划所处的分区（决定强调色、行内时间文案与可用操作）。 */
enum class ReviewPlanZone {
    Overdue, Today, Upcoming, Later, Paused, Completed
}

private val PillShape = RoundedCornerShape(ShapeTokens.Pill)

/** 进度点直径（原型 ring-dot 是 8px）。 */
private val StepDotSize = 8.dp

/**
 * 复习计划卡（对齐产品原型的 upcoming-item 形态）：标题 + 到期 chip +
 * 阶梯进度点 + 「第 N/M 次」+ 分区对应的操作。
 *
 * 从「书脊数据行」升级为「卡片」是这次重构的形态级变更（原型就是白卡 + 阴影）；
 * 卡片外壳走 [AppCard]，动森外观自动换成描边 + 暖褐投影 + 大圆角，**不用分主题写两套**。
 *
 * @param stepTotal 阶梯总档数（进度点的圆点数，由调用方从 `ReviewSchedulePolicy` 取）。
 * @param startOfToday 今天 0 点，「逾期 N 天 / N 天后」按它计算（天粒度）。
 */
@Composable
fun ReviewPlanCard(
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
    val scheme = MaterialTheme.colorScheme

    AppCard(
        modifier = modifier,
        onClick = onOpen,
        contentPadding = PaddingValues(Spacing.CardPadding)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = item.noteTitle,
                style = ZhiLuType.rowTitle,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            PlanTimeChip(
                label = zoneTimeLabel(zone, plan.nextReviewAt, startOfToday),
                zone = zone
            )
        }

        Row(
            modifier = Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepDots(
                currentStep = plan.currentStep,
                total = stepTotal,
                activeColor = accent
            )
            Text(
                text = "第 ${plan.currentStep.coerceAtMost(stepTotal - 1) + 1}/$stepTotal 次",
                style = ZhiLuType.meta,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        when (zone) {
            ReviewPlanZone.Overdue, ReviewPlanZone.Today -> {
                ActionRow {
                    OutlinedButton(onClick = onStart, shape = PillShape) {
                        Text("开始复习", style = ZhiLuType.chip)
                    }
                    QuietAction(label = "暂停", onClick = onPause)
                }
            }
            ReviewPlanZone.Paused -> {
                ActionRow {
                    OutlinedButton(onClick = onResume, shape = PillShape) {
                        Text("继续", style = ZhiLuType.chip)
                    }
                    QuietAction(label = "重新开始", onClick = onRestart)
                }
            }
            ReviewPlanZone.Completed -> {
                ActionRow {
                    OutlinedButton(onClick = onRestart, shape = PillShape) {
                        Text("重新开始", style = ZhiLuType.chip)
                    }
                }
            }
            // 未到期（接下来 / 之后）：仅展示，点整卡进笔记看内容。
            ReviewPlanZone.Upcoming, ReviewPlanZone.Later -> Unit
        }
    }
}

/** 卡片底部的操作行（统一样式；未到期时整行不渲染，不做留白占位）。 */
@Composable
private fun ActionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .height(32.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * 到期时间 chip：分区语义色的浅底小胶囊。
 *
 * 逾期用 error 系表达"需要立刻处理"，今天用 primary 系，其余走中性 ——
 * 色值全部是 md3 语义角色的透明变体，不写死 hex。
 */
@Composable
private fun PlanTimeChip(label: String, zone: ReviewPlanZone) {
    val scheme = MaterialTheme.colorScheme
    val (background, foreground) = when (zone) {
        ReviewPlanZone.Overdue -> scheme.error.copy(alpha = 0.14f) to scheme.error
        ReviewPlanZone.Today -> scheme.primary.copy(alpha = 0.14f) to scheme.primary
        ReviewPlanZone.Upcoming, ReviewPlanZone.Later,
        ReviewPlanZone.Paused, ReviewPlanZone.Completed ->
            scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    Surface(shape = CircleShape, color = background) {
        Text(
            text = label,
            style = ZhiLuType.meta,
            color = foreground,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
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
                    .size(StepDotSize)
                    .clip(CircleShape)
                    .background(if (index <= currentStep) activeColor else inactive)
            )
        }
    }
}

/** 分区 → 强调色（全部 md3 语义色，跟随明暗与两套外观）。 */
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
