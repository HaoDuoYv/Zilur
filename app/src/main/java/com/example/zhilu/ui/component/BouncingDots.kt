package com.example.zhilu.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.inkOnFill
import com.example.zhilu.ui.theme.palettePaint
import com.example.zhilu.ui.theme.shade

/**
 * 动森的「生成中」指示器：三颗**挨个弹起**的小球。
 *
 * 为什么不用参考仓库那个 `IslandAnimation`（会摇的树 + 游动的鱼）：那是**主视觉动画**，
 * 几百行矢量路径、还带 `System.currentTimeMillis()` 驱动的鱼 —— 放在一个长 12dp 的徽章里
 * 完全看不出是什么，却会拖慢每一次重组。这里取其**动势**（上下起伏、有节奏），
 * 做成三颗球，尺寸与原来的 `CircularProgressIndicator` 一致。
 *
 * 视觉上刻意**不是转圈**：动森那套东西没有"旋转"这个语汇，它的一切都是浮、沉、弹。
 * 每个球还带一圈厚度描边，和按钮/开关同一个材质。
 */
@Composable
fun BouncingDots(
    color: Color,
    modifier: Modifier = Modifier,
    dotSize: androidx.compose.ui.unit.Dp = 6.dp,
    /** 单颗球的完整周期。三颗依次错开 1/3 周期。 */
    periodMillis: Int = 900
) {
    val transition = rememberInfiniteTransition(label = "bouncing_dots")
    Row(
        modifier = modifier.height(dotSize * 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dotSize * 0.6f)
    ) {
        repeat(3) { index ->
            val lift by transition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = periodMillis
                        0f at 0 using LinearEasing
                        -1f at periodMillis / 3 using LinearEasing
                        0f at periodMillis * 2 / 3 using LinearEasing
                        0f at periodMillis
                    },
                    repeatMode = RepeatMode.Restart,
                    // 三颗错开 1/3 周期，形成"波浪"而不是"齐跳"
                    initialStartOffset = androidx.compose.animation.core.StartOffset(
                        offsetMillis = index * periodMillis / 3
                    )
                ),
                label = "dot_lift_$index"
            )
            Ball(color = color, size = dotSize, lift = lift)
        }
    }
}

@Composable
private fun Ball(color: Color, size: androidx.compose.ui.unit.Dp, lift: Float) {
    Box(
        modifier = Modifier
            .size(size)
            .offset(y = size * lift),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color, CircleShape)
                // 一圈同色相压暗的描边 —— 与按钮/开关同一个材质语言
                .border(size * 0.16f, shade(color).copy(alpha = 0.6f), CircleShape)
        )
    }
}

/** 当前外观下"生成中"该用哪种指示器。 */
@Composable
fun useBouncingDots(): Boolean =
    palettePaint(LocalThemePalette.current).componentBorder != Color.Unspecified

/** 动森指示器的字色：压在色块上的球用对比色。 */
@Composable
fun indicatorInk(on: Color): Color = inkOnFill(on)

private fun tween(durationMillis: Int) = tween<Float>(durationMillis)
