package com.example.zhilu.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 波浪分割线 —— 参考仓库 `AnimalIslandUI` 的 `wave_yellow` / `footer_sea` 那类装饰线。
 *
 * 参考仓库是**位图资源**（`R.drawable.wave_yellow`，固定 12dp 高、按 `FillHeight` 拉伸）。
 * 这里改成**现画的正弦波**，理由有三条：
 * ① 位图在高密度屏上会糊，而这是要出现在正文里的线；
 * ② 参考仓库那条是按自己的宽度比例配的，拉到别的容器宽上浪形会被压扁；
 * ③ 现画可以跟着主题取色，不用为两套外观各准备一张图。
 *
 * 波峰数是**按宽度算**的（不是写死个数），所以无论容器多宽，浪的疏密都一样。
 */
@Composable
fun WaveDivider(
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = WaveHeight,
    strokeWidth: Dp = 2.dp
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        // 每段波长固定，段数随宽度取整 —— 这样"浪的大小"与容器宽度无关
        val wavelength = 28.dp.toPx()
        val segments = (w / wavelength).coerceAtLeast(1f)
        val step = w / segments
        val amp = (h / 2f) - strokeWidth.toPx() / 2f

        val path = Path()
        path.moveTo(0f, mid)
        var x = 0f
        var up = true
        while (x < w - 0.5f) {
            val nextX = (x + step).coerceAtMost(w)
            // 用二次贝塞尔画半个波：控制点放在波峰/波谷，端点落在中线上
            path.quadraticTo(
                (x + nextX) / 2f,
                if (up) mid - amp else mid + amp,
                nextX,
                mid
            )
            x = nextX
            up = !up
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
    }
}

/** 波浪线高度：参考仓库是 12dp，这里取 10dp —— 它要落在正文之间，不能太抢。 */
val WaveHeight = 10.dp

/**
 * 虚线与实线共用的「手绘感」轻量线（参考仓库的 `divider_line_*` 那几张图）。
 *
 * 纸墨外观仍走发丝直线；动森下用这个**波浪 + 圆头**的版本，
 * 那点不规整是它整套 UI"不像表格"的来源之一。
 */
@Composable
fun HandDrawnDivider(
    color: Color,
    modifier: Modifier = Modifier,
    dashed: Boolean = false,
    strokeWidth: Dp = 2.dp
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(WaveHeight)
    ) {
        val mid = size.height / 2f
        drawLine(
            color = color,
            start = Offset(0f, mid),
            end = Offset(size.width, mid),
            strokeWidth = strokeWidth.toPx(),
            cap = StrokeCap.Round,
            pathEffect = if (dashed) {
                PathEffect.dashPathEffect(
                    floatArrayOf(10.dp.toPx(), 7.dp.toPx()),
                    0f
                )
            } else {
                null
            }
        )
    }
}
