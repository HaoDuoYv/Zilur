package com.example.zhilu.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.inkOnFill
import com.example.zhilu.ui.theme.motionSpring
import com.example.zhilu.ui.theme.palettePaint
import com.example.zhilu.ui.theme.shade

/** 动森开关的尺寸（参考仓库 DEFAULT 档）。 */
private val TrackWidth = 52.dp
private val TrackHeight = 28.dp
private val HandleSize = 21.dp
private val HandlePadding = 2.dp

/**
 * 开关。两套外观两种材质：
 * - **纸墨**：直接用 M3 的 [Switch]（一点没动）；
 * - **动森**：自绘 —— 胶囊轨道 + 2.5dp 描边 + **浮起的手柄**（参考仓库 `AnimalSwitch`）。
 *
 * 为什么要自绘而不是改 M3 的颜色：那套"手柄浮起"是**几何**上的事，不是配色能表达的 ——
 * 参考仓库给手柄压了一层圆形实心当投影、并让它 y 向上抬 2dp，看起来像能捏起来。
 * M3 的 `Switch` 把手柄尺寸、轨道、动画都焊在内部，改不动。
 *
 * 与原作的一处**有意偏离**：轨道开启色用当前**强调色**，不用参考仓库的 `SuccessColor`。
 * 这里绿色已经被语义角色「想法」占掉了，再拿绿色当"开启"会和内容色撞义（「一色一义」）。
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val paint = palettePaint(LocalThemePalette.current)
    if (paint.componentBorder == Color.Unspecified) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = modifier,
            colors = SwitchDefaults.colors()
        )
        return
    }

    val trackOn = MaterialTheme.colorScheme.primary
    val trackOff = MaterialTheme.colorScheme.surfaceVariant
    val track = if (checked) trackOn else trackOff
    val shape = RoundedCornerShape(percent = 50)
    val handlePosition by animateDpAsState(
        targetValue = if (checked) TrackWidth - HandleSize - HandlePadding * 2 else 0.dp,
        // motionSpring() 是 AnimationSpec<Float>，这里动画的是 Dp，得用 spring() 自己建
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "app_switch_handle"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(TrackWidth, TrackHeight)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = onCheckedChange
            )
            .alpha(if (enabled) 1f else DisabledAlpha),
        contentAlignment = Alignment.CenterStart
    ) {
        // 轨道：填充 + 描边（描边比填充深一档，形状才立得住）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(track, shape)
                .border(2.5.dp, shade(track), shape)
        )
        // 手柄：底下一层实心当厚度，面抬起来 2dp —— 参考仓库的核心手法
        Box(
            modifier = Modifier
                .padding(start = HandlePadding)
                .offset(x = handlePosition, y = (-1).dp)
                .size(HandleSize)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = 3.dp)
                    .background(shade(track), CircleShape)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(inkOnFill(track), CircleShape)
                    .border(2.dp, shade(track).copy(alpha = 0.6f), CircleShape)
            )
        }
    }
}

private const val DisabledAlpha = 0.5f
