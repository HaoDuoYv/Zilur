package com.example.zhilu.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 生成态脉冲透明度（0.35 ↔ 0.7 循环），用于「生成中」边框/覆盖层的呼吸动画。
 * 供知识卡片 / 块在 AI 生成占用期间调用。
 */
@Composable
fun rememberGeneratingPulse(): Float {
    val transition = rememberInfiniteTransition(label = "generatingPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "generatingPulseAlpha"
    )
    return alpha
}

/**
 * 「生成中」小徽章：指示器 + 文案，放在卡片/块右上角。
 *
 * 指示器跟着外观换：纸墨是常规转圈；动森是**三颗挨个弹起的小球**（见 [BouncingDots]）——
 * 那套 UI 里没有"旋转"这个语汇，它的一切都是浮、沉、弹。
 */
@Composable
fun GeneratingBadge(modifier: Modifier = Modifier) {
    val content = MaterialTheme.colorScheme.onPrimary
    Surface(
        modifier = modifier.alpha(0.95f),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = content
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (useBouncingDots()) {
                BouncingDots(color = content, dotSize = 5.dp)
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = content
                )
            }
            Text(
                text = "生成中",
                style = ZhiLuType.label,
                color = content
            )
        }
    }
}
