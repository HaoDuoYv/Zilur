package com.example.zhilu.ui.note.blocks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.AlphaTokens
import com.example.zhilu.ui.theme.LocalReducedMotion
import com.example.zhilu.ui.theme.MotionDuration
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.motionEnterTween
import com.example.zhilu.ui.theme.motionExitTween

/**
 * 块之间的插入槽：发丝线常显（编辑态的可发现入口），点击后浮出加号按钮完成插入。
 */
@Composable
fun BlockInsertIndicator(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Spacing.GutterTap),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Divider))
        )
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(motionEnterTween(MotionDuration.Short, enabled = !reducedMotion)),
            exit = fadeOut(motionExitTween(MotionDuration.Short, enabled = !reducedMotion))
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "插入块",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}