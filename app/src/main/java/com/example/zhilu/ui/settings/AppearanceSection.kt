package com.example.zhilu.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.zhilu.data.datastore.AccentColor
import com.example.zhilu.data.datastore.ThemeMode
import com.example.zhilu.ui.component.SegmentedToggle
import com.example.zhilu.ui.theme.LocalExtendedColors
import com.example.zhilu.ui.theme.LocalReducedMotion
import com.example.zhilu.ui.theme.MotionDuration
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.accentPaint

/**
 * 色板圆的直径。选中项放大到 [SwatchSelectedSize]，靠尺寸差一眼定位。
 *
 * **上限是单元格宽度（约 37.7dp），不是想要多大就多大**：`Row` 里的 `weight(1f)`
 * 会把 `maxWidth` 下发给子节点，圆一旦超过这一栏的宽度就会被横向压扁——
 * 实测选中项名义 40dp 时竖直径仍是 160px、横直径只剩 150px，
 * 渲染出来是一枚左右被削平的椭圆。这是肉眼很难发现、但一量就露馅的那类缺陷。
 */
private val SwatchSize = 30.dp
private val SwatchSelectedSize = 36.dp

/** 点击盒高度：色板圆本身不足 48dp，靠外层透明区域把触控目标补齐。 */
private val SwatchTouchTarget = 48.dp

/** 选中圆内的对钩尺寸：占圆直径约 55%，不贴边。 */
private val SwatchCheckSize = 20.dp

/** 外观：主题模式三段控件 + 强调色 7 色板。 */
@Composable
fun AppearanceSection(
    themeMode: ThemeMode,
    accentColor: AccentColor,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectAccent: (AccentColor) -> Unit
) {
    SettingsGroup(title = "外观") {
        SegmentedToggle(
            options = listOf(
                ThemeMode.SYSTEM to "跟随系统",
                ThemeMode.LIGHT to "浅色",
                ThemeMode.DARK to "深色"
            ),
            selected = themeMode,
            onSelect = onSelectThemeMode,
            modifier = Modifier.padding(vertical = Spacing.Xs)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "强调色",
                style = ZhiLuType.rowTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            // 色板本身只有颜色没有名字，选中的是哪一档得靠这里说出来。
            Text(
                text = accentPaint(accentColor).label,
                style = ZhiLuType.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AccentSwatchRow(
            selected = accentColor,
            onSelect = onSelectAccent
        )
    }
}

/**
 * 7 色色板。
 *
 * 七个圆平均分满一行（卡片内容宽 288dp，`weight(1f)` 后每格约 37.7dp）。
 * 由此得到两条硬约束：圆直径不能超过 37.7dp（超了会被横向压扁，见 [SwatchSize]），
 * 选中指示也只能画在圆内——画外圈的话加一圈描边就会啃到邻居，而 `weight` 已经没有多余宽度让出去了。
 */
@Composable
private fun AccentSwatchRow(
    selected: AccentColor,
    onSelect: (AccentColor) -> Unit,
    modifier: Modifier = Modifier
) {
    val darkTheme = LocalExtendedColors.current.isDark
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)
    ) {
        AccentColor.entries.forEach { accent ->
            AccentSwatch(
                accent = accent,
                selected = accent == selected,
                darkTheme = darkTheme,
                onSelect = { onSelect(accent) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AccentSwatch(
    accent: AccentColor,
    selected: Boolean,
    darkTheme: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val paint = accentPaint(accent)
    // 预览色板必须显示「当前深浅模式下的主色」，否则浅色模式挑的色到了深色模式下
    // 会变成完全另一个观感——用户是在为两套方案同时做选择。
    val roles = if (darkTheme) paint.dark else paint.light
    val reducedMotion = LocalReducedMotion.current
    val size by animateDpAsState(
        targetValue = if (selected) SwatchSelectedSize else SwatchSize,
        animationSpec = if (reducedMotion) snap() else tween(MotionDuration.Short),
        label = "accent_swatch_size"
    )

    Box(
        modifier = modifier
            .height(SwatchTouchTarget)
            // selectable 而非 clickable：一组互斥的色板就是单选组，
            // TalkBack 需要读到「已选中 / 未选中」而不是含糊的「可点击」。
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect
            )
            .semantics { contentDescription = paint.label },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(roles.primary),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    // 必须用角色里的 onPrimary 而不是「看着像」的白色：
                    // 深色模式下主色是浅色调，白色对钩会直接消失。
                    tint = roles.onPrimary,
                    contentDescription = null,
                    modifier = Modifier.size(SwatchCheckSize)
                )
            }
        }
    }
}
