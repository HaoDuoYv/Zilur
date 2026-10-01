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
 * 知录动效常量（精炼纸墨）。
 *
 * 时长整体收短、意图化：按压反馈最快，状态切换 240ms，页面转场 320ms，
 * 强调型转场（FAB 展开等）420ms。曲线统一走 [MotionEasing.Standard]。
 */
object MotionDuration {
    const val Quick = 100        // press / micro feedback
    const val Short = 140        // save status, icon feedback
    const val Medium = 240       // card entrance, focus, state change
    const val Long = 320         // page transition
    const val Emphasized = 420   // FAB expand, emphasized entrances
}

object MotionEasing {
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EaseOutCubic: Easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
    val EaseInOutCubic: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    // Enter / exit emphasis curves
    val Enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

// Springs for gesture-driven feedback (drag scale, FAB rebound). Prefer springs over tweens
// wherever the animation tracks a finger, so the response feels "跟手".
object MotionSpring {
    // 按压反馈：快、几乎无回弹
    val Press = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )
    // 位移/跟随：中等阻尼
    val Move = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
    // 弹层/展开：轻回弹
    val Sheet = spring<Float>(
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