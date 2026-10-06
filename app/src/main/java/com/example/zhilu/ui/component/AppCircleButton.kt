package com.example.zhilu.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.palettePaint
import com.example.zhilu.ui.theme.shade
/**
 * 圆形操作按钮（助手的发送/停止）。
 *
 * 动森下是**有厚度的实体圆钮**（参考仓库对手柄、勾选框用的同一手法）：底下一层同色
 * 实心当厚度，面抬起来，按下时面落到厚度上。纸墨仍是原来的平面圆。
 */
@Composable
fun AppCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** 面与厚度的基色。 */
    color: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val paint = palettePaint(LocalThemePalette.current)
    val toy = paint.componentBorder != Color.Unspecified
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    ToySurface(
        faceHeight = ButtonSize,
        modifier = modifier.size(ButtonSize + if (toy) DefaultThickness else 0.dp),
        shape = CircleShape,
        faceColor = color,
        thicknessColor = shade(color),
        thickness = if (toy) DefaultThickness else 0.dp,
        pressed = pressed,
        enabled = enabled
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(ButtonSize),
            shape = CircleShape,
            color = Color.Transparent,
            contentColor = contentColor
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}

/** 面尺寸；动森下整体还会再加一层厚度。 */
private val ButtonSize = 40.dp
