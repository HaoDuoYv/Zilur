package com.example.zhilu.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Motion constants for the ZhiLu design system.
 *
 * Durations are intentionally short and understated to match the editorial, content-first
 * aesthetic. Use the tween specs below for consistent enter/exit transitions.
 */
object MotionDuration {
    const val Quick = 100        // press / micro feedback
    const val Short = 150        // save status, icon feedback
    const val Medium = 300       // card entrance, focus
    const val Long = 500         // page transition
    const val Emphasized = 600   // FAB expand, emphasized entrances
}

object MotionEasing {
    val Standard: Easing = FastOutSlowInEasing
    val EaseOutCubic: Easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
    val EaseInOutCubic: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    // Material 3 emphasized curves for enter/exit emphasis
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

// Springs for gesture-driven feedback (drag scale, FAB rebound). Prefer springs over tweens
// wherever the animation tracks a finger, so the response feels "跟手".
object MotionSpring {
    val Snappy = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
    val Bouncy = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}

// True when the system requests "remove animations" (animator / transition scale = 0).
// Consume it to snap motion instead of animating, for accessibility.
val LocalReducedMotion = staticCompositionLocalOf { false }

// Default Compose spring feel, but snaps to target when reduced motion is requested.
@Composable
fun motionSpring(): AnimationSpec<Float> =
    if (LocalReducedMotion.current) {
        snap()
    } else {
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    }

fun <T> motionEnterTween(
    durationMillis: Int = MotionDuration.Medium,
    delayMillis: Int = 0,
    easing: Easing = MotionEasing.Standard,
    enabled: Boolean = true
) = tween<T>(
    durationMillis = if (enabled) durationMillis else 0,
    delayMillis = if (enabled) delayMillis else 0,
    easing = easing
)

fun <T> motionExitTween(
    durationMillis: Int = MotionDuration.Short,
    delayMillis: Int = 0,
    easing: Easing = MotionEasing.Standard,
    enabled: Boolean = true
) = tween<T>(
    durationMillis = if (enabled) durationMillis else 0,
    delayMillis = if (enabled) delayMillis else 0,
    easing = easing
)
