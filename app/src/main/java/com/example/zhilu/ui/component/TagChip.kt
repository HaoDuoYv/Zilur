package com.example.zhilu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.ui.theme.AlphaTokens
import com.example.zhilu.ui.theme.LocalExtendedColors
import com.example.zhilu.ui.theme.TagColorsNeutral
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.darkTagColor

enum class TagChipSize { Sm, Md, Lg }

/**
 * 把标签色解析为当前主题下应显示的颜色（深色下提亮以保证对比度）。
 *
 * 列表行色书脊、标签页圆点、标签胶囊三处必须共用它，否则同一个标签在不同页面颜色不一致。
 */
@Composable
fun rememberTagAccent(tagColor: Int?, colorOverride: Color? = null): Color {
    val isDark = LocalExtendedColors.current.isDark
    val base = colorOverride ?: Color(tagColor ?: TagColorsNeutral)
    return remember(base, isDark) { if (isDark) darkTagColor(base.toArgb()) else base }
}

/**
 * 标签胶囊。选中态用标签色 tint + 勾选图标（不只靠颜色区分，色盲友好）；
 * 深色主题下标签色自动提亮以保证对比度。
 */
@Composable
fun TagChip(
    tag: Tag,
    selected: Boolean = false,
    enabled: Boolean = true,
    size: TagChipSize = TagChipSize.Md,
    onClick: () -> Unit = {}
) {
    val tagColor = rememberTagAccent(tagColor = tag.color)
    val backgroundColor = if (selected) {
        tagColor.copy(alpha = AlphaTokens.Hover)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) tagColor else MaterialTheme.colorScheme.onSurfaceVariant
    val metrics = tagChipMetrics(size)

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = if (enabled) Modifier.minimumInteractiveComponentSize() else Modifier,
        shape = CircleShape,
        color = backgroundColor,
        shadowElevation = 0.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(metrics.padding)
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(metrics.dot + 4.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(metrics.dot)
                        .clip(CircleShape)
                        .background(tagColor)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = tag.name,
                style = metrics.textStyle,
                color = contentColor
            )
        }
    }
}

private data class TagChipMetrics(
    val dot: Dp,
    val padding: PaddingValues,
    val textStyle: androidx.compose.ui.text.TextStyle
)

private fun tagChipMetrics(size: TagChipSize): TagChipMetrics = when (size) {
    TagChipSize.Sm -> TagChipMetrics(
        dot = 8.dp,
        padding = PaddingValues(horizontal = 10.dp, vertical = 3.dp),
        textStyle = ZhiLuType.label
    )
    TagChipSize.Md -> TagChipMetrics(
        dot = 10.dp,
        padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        textStyle = ZhiLuType.chip
    )
    TagChipSize.Lg -> TagChipMetrics(
        dot = 10.dp,
        padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        textStyle = ZhiLuType.bodySmall
    )
}