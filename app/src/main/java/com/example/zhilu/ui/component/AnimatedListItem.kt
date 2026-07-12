package com.example.zhilu.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.zhilu.ui.theme.MotionDuration
import com.example.zhilu.ui.theme.MotionEasing
import com.example.zhilu.ui.theme.motionEnterTween
import kotlinx.coroutines.delay

/**
 * Wraps list content with a staggered fade + slide reveal.
 *
 * Items are delayed by [staggerDelayMillis] * index up to [maxIndex], then fade in while
 * sliding up slightly. Durations and easing come from the ZhiLu motion system.
 */
@Composable
fun AnimatedListItem(
    index: Int,
    modifier: Modifier = Modifier,
    maxIndex: Int = 12,
    staggerDelayMillis: Int = 40,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(staggerDelayMillis.toLong() * index.coerceAtMost(maxIndex))
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(
            motionEnterTween(
                durationMillis = MotionDuration.Medium,
                easing = MotionEasing.EaseOutCubic
            )
        ) + slideInVertically(
            motionEnterTween(
                durationMillis = MotionDuration.Medium,
                easing = MotionEasing.EaseOutCubic
            )
        ) { it / 5 }
    ) {
        content()
    }
}
