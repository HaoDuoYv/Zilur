package com.example.zhilu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.zhilu.data.datastore.ThemePalette
import com.example.zhilu.ui.theme.LocalThemePalette

/**
 * 「玩具按钮」——参考仓库 `AnimalIslandUI` 里最有辨识度的一招。
 *
 * 它不是常规投影，而是**两层实体**：底下一层同色实心块当"厚度"，上面那层往上抬
 * 当"面"。按下去的时候面会**落到厚度上**，看起来是真的被按下去了。
 * 常规 `shadow()` 给不出这个效果 —— 阴影是虚的，而厚度是实的。
 *
 * 为什么值得单独抽一个组件：参考仓库里 Button / Switch 手柄 / Checkbox **全都**用这个手法，
 * 它是那套 UI"像玩具"的主要来源。只改配色是换不出这个观感的。
 *
 * ## 尺寸由调用方给死，不要靠 `matchParentSize()`
 *
 * 第一版用 `Modifier.matchParentSize()` 让两层对齐外层，结果外层若**没有固定尺寸**
 * （比如只有 `wrapContentSize` 的圆钮），厚度层就退化成 0 高度、按钮整个消失。
 * 现在高度是显式参数，外层尺寸由它决定，不存在这个退化路径。
 *
 * 纸墨外观下 [thickness] 传 0，就是普通平面。
 */
@Composable
fun ToySurface(
    faceHeight: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
    faceColor: Color,
    thicknessColor: Color,
    /** 厚度（dp）。0 = 不加，就是普通平面。 */
    thickness: Dp = DefaultThickness,
    /** 按下时面下沉到多少。默认沉到只剩 1dp，做出"按到底"的手感。 */
    pressedLift: Dp = 1.dp,
    pressed: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val lift = when {
        !enabled -> thickness / 2
        pressed -> pressedLift
        else -> thickness
    }
    val faceAlpha = if (enabled) 1f else 0.5f
    // 外层总高固定 = 面高 + 厚度，所以按下时**不会有任何布局位移**。
    Box(modifier = modifier.height(faceHeight + thickness), contentAlignment = Alignment.TopCenter) {
        // 厚度层：不偏移，留在原地当"实体"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(faceHeight)
                .offset(y = thickness)
                .clip(shape)
                .background(thicknessColor.copy(alpha = faceAlpha))
        )
        // 面层：往上抬 lift
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(faceHeight)
                .offset(y = lift - thickness)
                .clip(shape)
                .background(faceColor.copy(alpha = faceAlpha)),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/** 默认厚度：参考仓库按钮是 5dp、手柄是 3dp。取 4dp 折中。 */
val DefaultThickness = 4.dp

/**
 * 当前外观是否该用「玩具」那一套（厚度 + 描边 + 大圆角）。
 *
 * 全局只有这一个判断点：各组件读它决定走"纸"还是走"塑料"，
 * 而不是各自去比 `ThemePalette.ANIMAL_ISLAND`。
 */
@Composable
fun useToyTreatment(): Boolean = LocalThemePalette.current == ThemePalette.ANIMAL_ISLAND
