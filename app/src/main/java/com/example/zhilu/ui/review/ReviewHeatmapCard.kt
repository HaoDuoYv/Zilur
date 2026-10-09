package com.example.zhilu.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zhilu.domain.model.ReviewHeatmap
import com.example.zhilu.ui.component.AppCard
import com.example.zhilu.ui.theme.HeatmapLevelPalette
import com.example.zhilu.ui.theme.LocalExtendedColors
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.palettePaint
import java.time.Instant
import java.time.ZoneId

/** 单格边长（原型 14px）；行内其余间距与之配套。 */
private val CellSize = 14.dp
private val CellGap = 3.dp
private val CellShape = RoundedCornerShape(4.dp)

/** 网格行数 = 一周七天；列数 = [ReviewHeatmap.WEEKS]。 */
private const val RowsPerWeek = 7

private val RowLabels = listOf("一", "", "三", "", "五", "", "日")

/**
 * 复习热力图卡（GitHub 风格网格）：提示条 + 15 周 × 7 天网格 + 「少 → 多」图例。
 *
 * 复刻产品原型的三段结构；点格子更新顶部提示条并选中（双层描边 + 轻震动）。
 * 五档强度色：零档用 `surfaceVariant`（空底融进卡片），一至四档用
 * **固定绿色阶梯**（[HeatmapLevelPalette]，对齐产品原型）——刻意不跟随主题色，
 * 纸墨 / 动森 × 明暗四个象限都是同一组绿。
 *
 * 行标签（一 / 三 / 五 / 日）对应**列 = 周、行 = 星期**的对齐口径，
 * 由 [ReviewHeatmap.window] 保证；[ReviewHeatmap.todayIndex] 之后的格子画成
 * 空心表示「未来」。
 */
@Composable
fun ReviewHeatmapCard(
    heatmap: ReviewHeatmap,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault()
) {
    val scheme = MaterialTheme.colorScheme
    val paint = palettePaint(LocalThemePalette.current)
    val bordered = paint.componentBorder != Color.Unspecified
    val haptics = LocalHapticFeedback.current

    // 选中格位（-1 = 未选中）。纯展示态，配置变更后保留即可。
    var selectedIndex by rememberSaveable { mutableIntStateOf(-1) }

    val emptyColor = scheme.surfaceVariant
    val levels = remember(emptyColor) { listOf(emptyColor) + HeatmapLevelPalette }

    AppCard(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        // ---- 提示条：未选中给引导语，选中后换成「日期：复习了 N 次」 ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            if (selectedIndex in heatmap.counts.indices) {
                val count = heatmap.counts[selectedIndex]
                val date = heatmapCellDate(heatmap.windowStart, selectedIndex, zone)
                Text(
                    text = if (count == 0) "$date：没有复习记录 ☕️" else "$date：复习了 $count 次 🎉",
                    style = ZhiLuType.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "点击下方网格查看详情",
                    style = ZhiLuType.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---- 网格：每行 = 星期几（行首标签），每列 = 一周 ----
        Column(verticalArrangement = Arrangement.spacedBy(CellGap)) {
            repeat(RowsPerWeek) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CellGap)
                ) {
                    Text(
                        text = RowLabels[row],
                        style = ZhiLuType.meta.copy(fontSize = 10.sp),
                        color = LocalExtendedColors.current.inkFaint,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.width(CellSize)
                    )
                    repeat(ReviewHeatmap.WEEKS) { col ->
                        val index = col * RowsPerWeek + row
                        HeatmapCell(
                            level = heatmap.levelAt(index),
                            isFuture = index > heatmap.todayIndex,
                            isSelected = index == selectedIndex,
                            colors = levels,
                            bordered = bordered,
                            borderColor = paint.componentBorder,
                            onClick = {
                                selectedIndex = index
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ---- 月份轴标：与网格同宽，取首 / 中 / 末三个采样点 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = CellSize + CellGap),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            heatmapMonthLabels(heatmap.windowStart, zone).forEach { label ->
                Text(
                    text = label,
                    style = ZhiLuType.meta.copy(fontSize = 10.sp),
                    color = LocalExtendedColors.current.inkFaint
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---- 图例：少 → 多 ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "少",
                style = ZhiLuType.meta.copy(fontSize = 10.sp),
                color = LocalExtendedColors.current.inkFaint
            )
            Spacer(modifier = Modifier.width(6.dp))
            levels.forEach { color ->
                Box(
                    modifier = Modifier
                        .padding(start = 3.dp)
                        .size(11.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "多",
                style = ZhiLuType.meta.copy(fontSize = 10.sp),
                color = LocalExtendedColors.current.inkFaint
            )
        }
    }
}

/** 单元格：正常按档取色；未来格为空心；选中格叠双层描边（外主色、内卡片底）。 */
@Composable
private fun HeatmapCell(
    level: Int,
    isFuture: Boolean,
    isSelected: Boolean,
    colors: List<Color>,
    bordered: Boolean,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    // 未选中且非未来时：动森外观给格子也描一圈细边（与卡片同材质逻辑）。
    val outline = if (bordered && !isFuture) borderColor.copy(alpha = 0.35f) else Color.Unspecified
    Box(
        modifier = modifier
            .height(CellSize)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, scheme.primary, CellShape)
                } else if (outline != Color.Unspecified) {
                    Modifier.border(1.dp, outline, CellShape)
                } else {
                    Modifier
                }
            )
            .padding(if (isSelected) 2.dp else 0.dp)
            .clip(CellShape)
            .background(if (isFuture) Color.Transparent else colors[level.coerceIn(colors.indices)])
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, scheme.surface, CellShape)
                } else if (isFuture) {
                    Modifier.border(1.dp, scheme.outlineVariant, CellShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
    )
}

/**
 * 格位 → 「10月9日」。
 *
 * 起点用 [ReviewHeatmap.windowStart]（与数据分桶同一份口径），
 * 按自然日推进 —— 不用「起点 + index × 86400000」自行推算，
 * 跨夏令时会让后者错一天。
 */
internal fun heatmapCellDate(
    windowStart: Long,
    index: Int,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val date = Instant.ofEpochMilli(windowStart).atZone(zone).toLocalDate().plusDays(index.toLong())
    return "${date.monthValue}月${date.dayOfMonth}日"
}

/**
 * 月份轴标：取网格**首列 / 中间列 / 末列**三个采样点的月份。
 *
 * 三个采样点与网格共用 [ReviewHeatmap.windowStart]，不再独立推算 ——
 * 「轴标与分桶共用窗口起点」是复习统计区的旧账（轴标曾整体错位成 9…15 天）。
 * 相邻重复的月份去重（15 周只跨 2 个月时只剩两个标签）。
 */
internal fun heatmapMonthLabels(
    windowStart: Long,
    zone: ZoneId = ZoneId.systemDefault()
): List<String> {
    val first = Instant.ofEpochMilli(windowStart).atZone(zone).toLocalDate()
    return listOf(0L, ReviewHeatmap.DAYS / 2L, ReviewHeatmap.DAYS - 1L)
        .map { offset -> "${first.plusDays(offset).monthValue}月" }
        .distinct()
}
