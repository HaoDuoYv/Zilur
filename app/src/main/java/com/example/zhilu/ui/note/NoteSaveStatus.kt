package com.example.zhilu.ui.note

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.LocalReducedMotion
import com.example.zhilu.ui.theme.MotionDuration
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.motionEnterTween
import com.example.zhilu.ui.theme.motionExitTween

/**
 * 保存状态细胶囊：圆点/进度 + 文案，仅在非空闲态出现于顶栏。
 */
@Composable
fun NoteSaveStatus(
    status: SaveStatus,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    AnimatedContent(
        targetState = status,
        modifier = modifier,
        transitionSpec = {
            fadeIn(
                motionEnterTween(MotionDuration.Short, enabled = !reducedMotion)
            ) togetherWith fadeOut(
                motionExitTween(MotionDuration.Short, enabled = !reducedMotion)
            )
        },
        label = "NoteSaveStatus"
    ) { target ->
        when (target) {
            SaveStatus.SAVING -> StatusCapsule(
                label = "保存中",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                leading = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            SaveStatus.SAVED -> StatusCapsule(
                label = "已保存",
                color = MaterialTheme.colorScheme.primary,
                leading = { StatusDot(color = MaterialTheme.colorScheme.primary) }
            )

            SaveStatus.ERROR -> StatusCapsule(
                label = "保存失败",
                color = MaterialTheme.colorScheme.error,
                leading = { StatusDot(color = MaterialTheme.colorScheme.error) }
            )

            SaveStatus.IDLE -> Spacer(modifier = Modifier.width(0.dp))
        }
    }
}

@Composable
private fun StatusCapsule(
    label: String,
    color: Color,
    leading: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = color.copy(alpha = 0.10f),
        contentColor = color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading()
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, style = ZhiLuType.label, color = color)
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Spacer(
        modifier = Modifier
            .size(6.dp)
            .background(color, CircleShape)
    )
}