package com.example.zhilu.ui.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.LocalExtendedColors
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.Radius
import com.example.zhilu.ui.theme.palettePaint

/** 动森弹层的外形：顶部两角大圆角，底部贴屏不切角。 */
private val AnimalSheetShape = RoundedCornerShape(
    topStart = Radius.SheetAnimalIsland,
    topEnd = Radius.SheetAnimalIsland
)

private val SheetBorderWidth = 2.dp

/**
 * 底部弹层。两套外观两种形态：
 * - **纸墨**：M3 默认（28dp 顶角、无描边、默认容器色）；
 * - **动森**：顶角加大到 [Radius.SheetAnimalIsland] + `componentBorder` 暖褐描边 +
 *   surface 容器 ——「大圆角 + 描边」是参考仓库 Card / Modal 一族的形状特征。
 *
 * 描边为什么是自绘而不是 `Modifier.border`：M3 1.3 的 [ModalBottomSheet] 没有 border 参数，
 * 而 `Modifier.border` 画在内容之后、Surface 背景之下，会被自己的背景盖掉半条。
 * `drawWithContent` 在 Surface 画完之后再描一圈**同形状**的 outline，才是稳定做法。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit
) {
    val paint = palettePaint(LocalThemePalette.current)
    if (paint.componentBorder == Color.Unspecified) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            content = content
        )
        return
    }

    val darkTheme = LocalExtendedColors.current.isDark
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.drawWithContent {
            drawContent()
            drawOutline(
                outline = AnimalSheetShape.createOutline(size, layoutDirection, this),
                color = paint.componentBorder.copy(alpha = if (darkTheme) 0.7f else 1f),
                style = Stroke(width = SheetBorderWidth.toPx())
            )
        },
        sheetState = sheetState,
        shape = AnimalSheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        content = content
    )
}
