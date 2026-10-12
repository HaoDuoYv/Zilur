package com.example.zhilu.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.Radius
import com.example.zhilu.ui.theme.inkOnFill
import com.example.zhilu.ui.theme.palettePaint
import com.example.zhilu.ui.theme.shade

/** 动森勾选控件的尺寸（参考仓库 MIDDLE 档）。 */
private val ControlBoxSize = 22.dp
private val ControlBorderWidth = 2.5.dp
private val CheckIconSize = 13.dp
private val RadioDotSize = 10.dp
private const val ControlDisabledAlpha = 0.5f

/**
 * 勾选框。两套外观两种材质：
 * - **纸墨**：直接用 M3 的 [Checkbox]（一点没动）；
 * - **动森**：自绘 —— [Radius.ControlAnimalIsland] 圆角方框 + 2.5dp 描边 +
 *   **勾号弹簧**（参考仓库 `AnimalCheckbox`）。
 *
 * 几何规格照抄参考仓库 MIDDLE 档（22dp 框 / 2.5dp 描边 / 13dp 勾号）；
 * 勾号出场是 `scale 0→1` 的弹簧（dampingRatio 0.6 / stiffness 400，参考值），
 * 不是淡入 —— "弹出来"才是这套外观该有的手感。
 *
 * 描边色走 `componentBorder`（材质统一来源）；选中态用主色填充 + `shade()` 压深一档的
 * 描边，与 [AppSwitch] 同一套"填充色上再压一层"的自证形状手法。
 * 勾号颜色用 [inkOnFill] 从填充色推，不写死 White。
 *
 * [onCheckedChange] 传 null 表示**只显示不接收点击**（行点击已在外部接管）——
 * 与 M3 同名参数语义一致；会保留 M3 同款 48dp 最小触控尺寸，行布局不会因替换而位移。
 */
@Composable
fun AppCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val paint = palettePaint(LocalThemePalette.current)
    if (paint.componentBorder == Color.Unspecified) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled
        )
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "app_checkbox_check"
    )
    val fill = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val stroke = if (checked) shade(fill) else paint.componentBorder
    val shape = RoundedCornerShape(Radius.ControlAnimalIsland)

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Checkbox,
                        interactionSource = interactionSource,
                        indication = null,
                        onValueChange = onCheckedChange
                    )
                } else {
                    Modifier
                }
            )
            .alpha(if (enabled) 1f else ControlDisabledAlpha),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(ControlBoxSize)
                .background(fill, shape)
                .border(ControlBorderWidth, stroke, shape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = inkOnFill(fill),
                modifier = Modifier
                    .size(CheckIconSize)
                    .scale(checkScale)
            )
        }
    }
}

/**
 * 单选钮。两套外观两种材质：
 * - **纸墨**：直接用 M3 的 [RadioButton]（一点没动）；
 * - **动森**：自绘 —— 与 [AppCheckbox] 同规格的圆形版（参考仓库没有 Radio，
 *   几何取自 `AnimalCheckbox` MIDDLE 档，只把方框换成圆、勾号换成中心圆点）。
 *
 * 选中态：描边由 `componentBorder` 换主色、中心圆点 `scale 0→1` 弹入。
 * 与 [AppCheckbox] 一样保留 48dp 最小触控尺寸、`onClick = null` 只显示不响应。
 */
@Composable
fun AppRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val paint = palettePaint(LocalThemePalette.current)
    if (paint.componentBorder == Color.Unspecified) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled
        )
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "app_radio_dot"
    )
    val stroke = if (selected) MaterialTheme.colorScheme.primary else paint.componentBorder

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .then(
                if (onClick != null) {
                    Modifier.selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .alpha(if (enabled) 1f else ControlDisabledAlpha),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(ControlBoxSize)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .border(ControlBorderWidth, stroke, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(RadioDotSize)
                    .scale(dotScale)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}
