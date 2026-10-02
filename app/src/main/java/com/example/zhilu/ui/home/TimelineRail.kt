package com.example.zhilu.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/** 轨道在页面左侧留白里的横向位置（卡片从 `Spacing.PageGutter` = 20dp 起，这一带是空的）。 */
private val RailCenterX = 9.dp

/** 竖线粗细。 */
private val RailLineWidth = 2.dp

/** 节点圆点直径。 */
private val RailNodeDiameter = 9.dp

/**
 * 节点圆心距列表行顶部的高度。
 *
 * 由卡片自身的节奏推出来：外边距 5dp（`CardGap / 2`）+ 内边距 16dp（`CardPadding`）
 * + 标题行高 24dp 的一半 = 33dp，正好落在标题的视觉中线上。
 */
private val RailNodeY = 33.dp

/**
 * 时间线轨道：在卡片左侧的页面留白里画一条竖线 + 一个节点圆点。
 *
 * 每个列表行只画自己那一段——行高由卡片内容决定（卡片的上下外边距也在行内），
 * 段与段自然接上，不需要额外测量整组的高度。
 * 首行把线收在节点上（否则轨道会从分节标题里「长出来」），末行同理收在节点上。
 *
 * 画在卡片**背后**：左滑露出删除槽时卡片会往左盖过轨道，轨道不该跟着动。
 */
@Composable
internal fun Modifier.timelineRail(isFirst: Boolean, isLast: Boolean): Modifier {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val nodeColor = MaterialTheme.colorScheme.primary

    return drawBehind {
        val x = RailCenterX.toPx()
        val nodeY = RailNodeY.toPx()
        val halfLine = RailLineWidth.toPx() / 2f
        val top = if (isFirst) nodeY else 0f
        val bottom = if (isLast) nodeY else size.height

        if (bottom > top) {
            drawRect(
                color = lineColor,
                topLeft = Offset(x - halfLine, top),
                size = Size(RailLineWidth.toPx(), bottom - top)
            )
        }
        // 圆点后画，正好盖住同一条竖线，于是节点处不会有线头露出来。
        drawCircle(
            color = nodeColor,
            radius = RailNodeDiameter.toPx() / 2f,
            center = Offset(x, nodeY)
        )
    }
}
