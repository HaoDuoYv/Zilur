package com.example.zhilu.ui.note.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.AlphaTokens
import com.example.zhilu.ui.theme.ShapeTokens

@Composable
fun DividerBlockView(
    readOnly: Boolean,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier
) {
    val lineColor = if (isDragging) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val lineThickness = if (isDragging) 4.dp else 3.dp

    if (readOnly) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            DividerLine(
                thickness = lineThickness,
                color = lineColor
            )
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(ShapeTokens.Small))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = AlphaTokens.Subtle)),
            contentAlignment = Alignment.Center
        ) {
            DividerLine(
                thickness = lineThickness,
                color = lineColor,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun DividerLine(
    thickness: Dp,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .clip(RoundedCornerShape(thickness / 2))
            .background(color)
    )
}
