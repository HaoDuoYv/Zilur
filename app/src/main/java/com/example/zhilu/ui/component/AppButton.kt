package com.example.zhilu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.inkOnFill
import com.example.zhilu.ui.theme.palettePaint
import com.example.zhilu.ui.theme.shade

/**
 * 按钮语义档位。
 *
 * - [Primary]：页面/弹窗的主行动（保存、添加、开始复习、确认）；
 * - [Tonal]：次强调（空态主按钮，不跟主色抢第一眼）；
 * - [Outline]：卡片内的动作（暂停、继续、完成），描边语言；
 * - [Quiet]：弱化动作（取消、展开、清空、显示已完成）——两态都刻意不加厚度，
 *   厚度是"这是个实体控件"的强信号，Quiet 要的恰恰是不抢戏；
 * - [Danger]：破坏性确认（永久删除、清空回收站）。
 */
enum class AppButtonVariant { Primary, Tonal, Outline, Quiet, Danger }

/** 动森立体键的面高：参考仓库 MIDDLE 档按钮 45dp = 40dp 面 + 5dp 厚度，此处面取 40。 */
private val FaceHeight = 40.dp

/**
 * 全站按钮。两套外观两种材质：
 * - **纸墨**：平面，直接映射到 M3 原生四件套（Button / FilledTonalButton / OutlinedButton /
 *   TextButton），长什么样全交给 M3；
 * - **动森**：**玩具立体键**（参考仓库 `AnimalButton` 的核心手法）——面抬 4dp、底下压一层
 *   同色相压暗的厚度，按下时面落到厚度上（只剩 1dp）。常规 `shadow()` 给不出这个效果：
 *   阴影是虚的，厚度是实的。
 *
 * 为什么自绘而不是复用 [ToySurface]：后者的两层用 `fillMaxWidth()` 对齐**外层给定尺寸**
 * （见其 KDoc，它服务的是定尺寸圆钮/全宽底板）；按钮多数场景要 **wrap-content**（跟在输入框
 * 后面的"添加"、弹窗右下角两个动作并排），`fillMaxWidth` 在行内会把整行吃掉。这里用
 * `matchParentSize` 让厚度层跟随**面层**尺寸，两种宽度模式都成立。厚度取值与按下手感
 * 仍与 [ToySurface] 同源（[DefaultThickness]、沉到 1dp）。
 *
 * [fillWidth] 只在动森分支起作用（纸墨请用 `Modifier.fillMaxWidth()`，与 M3 一致）。
 * 破坏性动作请用 [AppButtonVariant.Danger]，别自己给文字上 error 色。
 */
@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.Primary,
    enabled: Boolean = true,
    fillWidth: Boolean = false,
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit
) {
    val paint = palettePaint(LocalThemePalette.current)
    val outer = if (fillWidth) modifier.fillMaxWidth() else modifier
    if (paint.componentBorder == Color.Unspecified) {
        // 纸墨：平面，各归各的 M3 原件
        when (variant) {
            AppButtonVariant.Primary -> Button(
                onClick = onClick,
                modifier = outer,
                enabled = enabled,
                contentPadding = contentPadding ?: ButtonDefaults.ContentPadding,
                content = content
            )

            AppButtonVariant.Tonal -> FilledTonalButton(
                onClick = onClick,
                modifier = outer,
                enabled = enabled,
                contentPadding = contentPadding ?: ButtonDefaults.ContentPadding,
                content = content
            )

            AppButtonVariant.Outline -> OutlinedButton(
                onClick = onClick,
                modifier = outer,
                enabled = enabled,
                contentPadding = contentPadding ?: ButtonDefaults.ContentPadding,
                content = content
            )

            AppButtonVariant.Quiet -> TextButton(
                onClick = onClick,
                modifier = outer,
                enabled = enabled,
                contentPadding = contentPadding ?: ButtonDefaults.TextButtonContentPadding,
                content = content
            )

            AppButtonVariant.Danger -> TextButton(
                onClick = onClick,
                modifier = outer,
                enabled = enabled,
                contentPadding = contentPadding ?: ButtonDefaults.TextButtonContentPadding,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                content = content
            )
        }
        return
    }

    // 动森：立体键
    val face = when (variant) {
        AppButtonVariant.Primary -> MaterialTheme.colorScheme.primary
        AppButtonVariant.Tonal -> MaterialTheme.colorScheme.primaryContainer
        AppButtonVariant.Outline -> MaterialTheme.colorScheme.surface
        AppButtonVariant.Quiet -> MaterialTheme.colorScheme.surface
        AppButtonVariant.Danger -> MaterialTheme.colorScheme.error
    }
    val ink = when (variant) {
        AppButtonVariant.Primary -> MaterialTheme.colorScheme.onPrimary
        AppButtonVariant.Tonal -> MaterialTheme.colorScheme.onPrimaryContainer
        AppButtonVariant.Outline -> MaterialTheme.colorScheme.primary
        AppButtonVariant.Quiet -> MaterialTheme.colorScheme.onSurfaceVariant
        AppButtonVariant.Danger -> inkOnFill(MaterialTheme.colorScheme.error)
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(percent = 50)
    val thickness = DefaultThickness
    // lift = 未遮住的厚度：不按 = 整层厚度；按下 = 只剩 1dp；禁用 = 半沉
    val lift = when {
        !enabled -> thickness / 2
        pressed -> 1.dp
        else -> thickness
    }
    val padding = contentPadding
        ?: PaddingValues(horizontal = if (variant == AppButtonVariant.Quiet) 12.dp else 24.dp)

    Box(
        modifier = outer
            .padding(bottom = thickness)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        // 厚度层：与面同尺寸（matchParentSize=面定下的尺寸），下移一个 thickness
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = thickness)
                .clip(shape)
                .background(shade(face))
        )
        // 面层：下沉 (thickness - lift)
        Row(
            modifier = Modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .offset(y = thickness - lift)
                .clip(shape)
                .background(face)
                .height(FaceHeight)
                .padding(padding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides ink) {
                content()
            }
        }
    }
}
