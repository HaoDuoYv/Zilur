package com.example.zhilu.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Switch
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
import com.example.zhilu.domain.model.EmphasisTone
import com.example.zhilu.ui.component.SegmentedToggle
import com.example.zhilu.ui.theme.LocalExtendedColors
import com.example.zhilu.ui.theme.LocalReducedMotion
import com.example.zhilu.ui.theme.MotionDuration
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.accentPaint
import com.example.zhilu.ui.theme.emphasisInkColor
import com.example.zhilu.ui.theme.emphasisToneColor

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

/** 每行放几个色板圆。见 [AccentSwatchRow] 关于"为什么不能全挤一行"的说明。 */
private const val SwatchesPerRow = 4

/** 外观：主题模式三段控件 + 强调色 7 色板 + 无障碍语义色板。 */
@Composable
fun AppearanceSection(
    themeMode: ThemeMode,
    accentColor: AccentColor,
    accessibleEmphasis: Boolean,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectAccent: (AccentColor) -> Unit,
    onToggleAccessibleEmphasis: (Boolean) -> Unit
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

        // 无障碍语义色板。开与不开的差别只有用户自己看得出来，所以配一排**实时预览**：
        // 预览直接按"开关切换后的状态"取色，不必真的切过去再回来看。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "色盲友好语义色",
                    style = ZhiLuType.rowTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "要点/想法/注意/待办改用色觉障碍下可分辨、且明度分级更明显的一套",
                    style = ZhiLuType.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = accessibleEmphasis,
                onCheckedChange = onToggleAccessibleEmphasis
            )
        }

        EmphasisPreviewRow(accessible = accessibleEmphasis)
    }
}

/** 四个语义角色的实时预览：圆点取块级色、文字取行内墨色，和正文里看到的一致。 */
@Composable
private fun EmphasisPreviewRow(
    accessible: Boolean,
    modifier: Modifier = Modifier
) {
    val darkTheme = LocalExtendedColors.current.isDark
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
    ) {
        EmphasisTone.entries.forEach { tone ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(emphasisToneColor(tone, darkTheme, accessible))
                )
                Text(
                    text = tone.label,
                    style = ZhiLuType.meta,
                    color = emphasisInkColor(tone, darkTheme, accessible)
                )
            }
        }
    }
}

/**
 * 强调色板。
 *
 * **每行 4 个**，不是全部挤一行：单元格宽度 = (卡片内容宽 − 间距) / 每行个数，
 * 而圆的直径不能超过单元格宽度（超了会被 `weight(1f)` 横向压扁成椭圆，
 * 见 [SwatchSize] 的说明）。7 色时一行勉强放得下（约 37.7dp），**加到 8 色就会塌**
 * （约 32.5dp < 选中态 36dp），所以这里按 4 个一行折行 —— 顺带也给后续加色留了余量。
 */
@Composable
private fun AccentSwatchRow(
    selected: AccentColor,
    onSelect: (AccentColor) -> Unit,
    modifier: Modifier = Modifier
) {
    val darkTheme = LocalExtendedColors.current.isDark
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Xs)
    ) {
        AccentColor.entries.chunked(SwatchesPerRow).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)
            ) {
                row.forEach { accent ->
                    AccentSwatch(
                        accent = accent,
                        selected = accent == selected,
                        darkTheme = darkTheme,
                        onSelect = { onSelect(accent) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // 最后一行不足时补空位，否则剩下的圆会被拉宽、与上一行对不齐
                repeat(SwatchesPerRow - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
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

    Column(
        modifier = modifier
            // selectable 而非 clickable：一组互斥的色板就是单选组，
            // TalkBack 需要读到「已选中 / 未选中」而不是含糊的「可点击」。
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect
            )
            .semantics { contentDescription = paint.label },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .height(SwatchTouchTarget),
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
        // 名字写在圆下面（设计原型 `.swname`）：8 个色相里有几对相邻色（赭土/燕麦、
        // 绛红/正红）光看色块分不清，靠名字才选得准。
        Text(
            text = paint.label,
            style = ZhiLuType.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
