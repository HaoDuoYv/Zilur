package com.example.zhilu.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
 * 「生成中」小徽章：转圈 + 文案，放在卡片/块右上角。
 */
@Composable
fun GeneratingBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.alpha(0.95f),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = "生成中",
                style = ZhiLuType.label,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
