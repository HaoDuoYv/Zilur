package com.example.zhilu.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.ShapeTokens
import com.example.zhilu.ui.theme.palettePaint

/**
 * 下拉菜单。两套外观共用 **12dp 圆角**（[ShapeTokens.Medium]，与纸卡同族的圆润度），
 * **描边随外观**：
 * - **纸墨**：无描边，容器色沿用 M3 默认；
 * - **动森**：`componentBorder` 暖褐描边 + surface 容器（参考仓库 `AnimalSelect` 菜单语言）。
 *
 * 菜单里的行（[DropdownMenuItem]）不换 —— 两套外观下都是"点一个就关"的同一语义，
 * 差异只在**容器**的圆角与描边，这也是本组件只包一层的边界。
 * 破坏性项（删除等）由调用方在 `DropdownMenuItem` 的 text 里自行用 `colorScheme.error` 表达。
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit
) {
    val paint = palettePaint(LocalThemePalette.current)
    val bordered = paint.componentBorder != Color.Unspecified

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = RoundedCornerShape(ShapeTokens.Medium),
        containerColor = if (bordered) {
            MaterialTheme.colorScheme.surface
        } else {
            MenuDefaults.containerColor
        },
        border = if (bordered) BorderStroke(2.dp, paint.componentBorder) else null,
        content = content
    )
}
