package com.example.zhilu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.ShapeTokens
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 空态：圆形图标 + 衬线标题 + 说明 + 主按钮。
 *
 * @param compact 紧凑变体：用于分节内的局部空态（无最小高度、更小图标）。
 */
@Composable
fun AppEmptyState(
    onAction: () -> Unit,
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String = "开始记录",
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (compact) Modifier else Modifier.heightIn(min = 260.dp))
            .padding(vertical = if (compact) 16.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 48.dp else 72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (compact) 22.dp else 30.dp)
            )
        }
        Text(
            text = title,
            style = if (compact) ZhiLuType.cardTitle else ZhiLuType.pageTitle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = if (compact) 10.dp else 16.dp, bottom = 8.dp)
        )
        Text(
            text = description,
            style = ZhiLuType.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
        )
        FilledTonalButton(
            onClick = onAction,
            shape = RoundedCornerShape(ShapeTokens.Pill),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(text = buttonText, style = ZhiLuType.chip)
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            TextButton(
                onClick = onSecondaryAction,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(secondaryActionLabel, style = ZhiLuType.chip)
            }
        }
    }
}