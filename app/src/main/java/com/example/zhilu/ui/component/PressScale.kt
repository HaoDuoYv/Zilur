package com.example.zhilu.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.example.zhilu.ui.theme.LocalReducedMotion
import com.example.zhilu.ui.theme.MotionSpring

/**
 * 统一的按压缩放反馈。调用方把同一个 [interactionSource] 同时交给 clickable/IconButton，
 * 反馈与点击命中共用一次交互状态，避免全站各写一套 animateFloatAsState。
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.9f,
    enabled: Boolean = true
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = if (reducedMotion) snap() else MotionSpring.Press,
        label = "press_scale"
    )
    return this.scale(scale)
}