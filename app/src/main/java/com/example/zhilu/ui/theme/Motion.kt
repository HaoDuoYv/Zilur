package com.example.zhilu.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

/**
 * Motion constants for the ZhiLu design system.
 *
 * Durations are intentionally short and understated to match the editorial, content-first
 * aesthetic. Use the tween specs below for consistent enter/exit transitions.
 */
object MotionDuration {
    const val Short = 150
    const val Medium = 300
    const val Long = 500
}

object MotionEasing {
    val Standard: Easing = FastOutSlowInEasing
    val EaseOutCubic: Easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
    val EaseInOutCubic: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
}

fun <T> motionEnterTween(
    durationMillis: Int = MotionDuration.Medium,
    delayMillis: Int = 0,
    easing: Easing = MotionEasing.Standard
) = tween<T>(
    durationMillis = durationMillis,
    delayMillis = delayMillis,
    easing = easing
)

fun <T> motionExitTween(
    durationMillis: Int = MotionDuration.Short,
    delayMillis: Int = 0,
    easing: Easing = MotionEasing.Standard
) = tween<T>(
    durationMillis = durationMillis,
    delayMillis = delayMillis,
    easing = easing
)
