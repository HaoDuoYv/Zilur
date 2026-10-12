package com.example.zhilu.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.AlphaTokens
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.palettePaint

/**
 * 通用胶囊（供应商预设 / 历史搜索词 / 任何"点一下填进去"的小动作）。
 *
 * 与 [TagChip] 同一套双态描边语言：动森下未选中也描 `componentBorder`，
 * 选中走主色 tint + 勾号（不只靠颜色传达选中，色盲友好）；纸墨下无描边、靠底色分层。
 * 文字与内边距规格与 [TagChip] Md 档一致（[ZhiLuType.chip]）。
 *
 * 与 [TagChip] 的分工：TagChip 带"标签色"维度（色点、色书脊），AppChip 没有 ——
 * 标签色是领域概念，通用胶囊不该认识它；也正因如此不把两者合并。
 */
@Composable
fun AppChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true
) {
    val paint = palettePaint(LocalThemePalette.current)
    val bordered = paint.componentBorder != Color.Unspecified
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Hover)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val strokeColor = when {
        !bordered -> Color.Unspecified
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        else -> paint.componentBorder
    }

    Surface(
        modifier = modifier
            .then(if (enabled) Modifier.minimumInteractiveComponentSize() else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = backgroundColor,
        border = if (bordered) BorderStroke(2.dp, strokeColor) else null,
        shadowElevation = 0.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = ZhiLuType.chip,
                color = contentColor
            )
        }
    }
}
