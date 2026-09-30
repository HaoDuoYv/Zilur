package com.example.zhilu.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 全站卡片视觉规范：统一圆角、描边与阴影。
 */
object AppCardStyle {
    val elevation = ElevationTokens.Card
    val borderWidth = 1.dp
}

/**
 * 统一阴影层级：替代散落的 0/0.5/4/6/8dp 字面量。
 */
object ElevationTokens {
    val Flat = 0.dp          // 平铺（标签、块）
    val Card = 0.5.dp        // 卡片常态
    val Raised = 3.dp        // 悬浮 / 聚焦
    val Floating = 6.dp      // FAB、拖拽拾起
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
    val shape = MaterialTheme.shapes.medium
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
    )
    val elevation = CardDefaults.cardElevation(defaultElevation = AppCardStyle.elevation)
    val border = BorderStroke(AppCardStyle.borderWidth, MaterialTheme.colorScheme.outlineVariant)

    if (onClick != null) {
        Card(
            modifier = cardModifier,
            onClick = onClick,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    } else {
        Card(
            modifier = cardModifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}
