package com.example.zhilu.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.zhilu.data.datastore.ThemePalette
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.paletteDescription
import com.example.zhilu.ui.theme.paletteLabel
import com.example.zhilu.ui.theme.palettePaint

/**
 * 外观选择器：每个外观一张**自己的配色缩略卡**。
 *
 * 为什么不做成纯文字单选：外观之间的差别就是"长什么样"，用当前主题的颜色去画一张
 * 预览卡，用户不用切过去再切回来试。缩略卡用的色值全部取自**被预览的那套外观**
 * （不是当前主题），否则两张卡会长得一样。
 */
@Composable
fun PalettePicker(
    selected: ThemePalette,
    onSelect: (ThemePalette) -> Unit,
    modifier: Modifier = Modifier,
    /** 缩略卡按浅色还是深色方案取色——跟当前明暗设置走，所见即所得。 */
    darkTheme: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
    ) {
        ThemePalette.entries.forEach { palette ->
            PaletteOption(
                palette = palette,
                selected = palette == selected,
                darkTheme = darkTheme,
                onSelect = { onSelect(palette) }
            )
        }
    }
}

@Composable
private fun PaletteOption(
    palette: ThemePalette,
    selected: Boolean,
    darkTheme: Boolean,
    onSelect: () -> Unit
) {
    val paint = palettePaint(palette)
    val scheme = paint.scheme(darkTheme)
    val label = paletteLabel(palette)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // selectable 而非 clickable：互斥的一组就是单选组，
            // TalkBack 要读到「已选中 / 未选中」而不是含糊的「可点击」。
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = "$label 外观" }
            .padding(vertical = Spacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PaletteThumbnail(
            background = scheme.background,
            card = scheme.surface,
            primary = scheme.primary,
            secondary = scheme.secondary,
            tertiary = scheme.tertiary,
            cornerRadius = if (palette == ThemePalette.ANIMAL_ISLAND) 14.dp else 8.dp,
            borderColor = scheme.outlineVariant
        )

        Spacer(modifier = Modifier.width(Spacing.Md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = ZhiLuType.rowTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = paletteDescription(palette),
                style = ZhiLuType.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                // 用当前主题的主色画对钩：它同时在告诉用户"你的强调色是哪个"
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 配色缩略卡：底色 + 一张小卡 + 三个色点。
 *
 * 三个色点对应 primary / secondary / tertiary —— 这正是两套外观差别最大的地方
 * （纸墨是低饱和中性三色，动森是草绿/桃粉/天蓝）。
 */
@Composable
private fun PaletteThumbnail(
    background: androidx.compose.ui.graphics.Color,
    card: androidx.compose.ui.graphics.Color,
    primary: androidx.compose.ui.graphics.Color,
    secondary: androidx.compose.ui.graphics.Color,
    tertiary: androidx.compose.ui.graphics.Color,
    cornerRadius: androidx.compose.ui.unit.Dp,
    borderColor: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .size(width = 72.dp, height = 48.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // 小卡：代表卡面/浮层
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(cornerRadius / 2))
                    .background(card)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(primary, secondary, tertiary).forEach { dot ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dot)
                    )
                }
            }
        }
    }
}
