package com.example.zhilu.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.zhilu.ui.theme.LocalReducedMotion
import com.example.zhilu.ui.theme.MotionDuration
import com.example.zhilu.ui.theme.motionEnterTween

@Composable
fun AppFAB(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Add,
    text: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = motionEnterTween(MotionDuration.Short, enabled = !reducedMotion),
        label = "fab_press_scale"
    )

    val fabModifier = modifier.scale(scale)
    val shape = MaterialTheme.shapes.extraLarge
    val containerColor = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onPrimary

    if (text.isNullOrEmpty()) {
        FloatingActionButton(
            onClick = onClick,
            modifier = fabModifier,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            interactionSource = interactionSource
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    } else {
        ExtendedFloatingActionButton(
            onClick = onClick,
            modifier = fabModifier,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            interactionSource = interactionSource,
            icon = { Icon(imageVector = icon, contentDescription = contentDescription) },
            text = { Text(text = text, style = MaterialTheme.typography.labelLarge) }
        )
    }
}
