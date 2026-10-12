package com.example.zhilu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.zhilu.ui.theme.LocalExtendedColors
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.animalHandDrawnShape
import com.example.zhilu.ui.theme.palettePaint

/**
 * 全站确认弹窗。两套外观两种形态：
 * - **纸墨**：M3 原生 [AlertDialog]，一点没动；
 * - **动森**：手作外形（[animalHandDrawnShape]，四角半径刻意不等）+ `componentBorder`
 *   暖褐描边 + 右上**飘出式**关闭钮（参考仓库 `AnimalModal`）；按钮走 [AppButton]，
 *   在动森下自然成为两个立体键。
 *
 * 外形**不是**参考仓库 `AnimalModalShape` 那圈贝塞尔：那条路本仓库走过一次、
 * 渲染出错且不报错（见 `IrregularShape.kt` 的"别再走回去"），改用手作圆角 ——
 * 观感来源相同（四角不等），路径由框架生成，不存在方向写反的可能。
 *
 * 按钮从次要到主要排在右下角：先 [dismissButton]（若有）再 [confirmButton]，
 * 与参考仓库 Cancel+Confirm 同序 —— "确认在右下"是两套外观共同的手势习惯。
 * 破坏性确认请用 `AppButton(AppButtonVariant.Danger)`。
 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val paint = palettePaint(LocalThemePalette.current)
    if (paint.componentBorder == Color.Unspecified) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(title) },
            text = { content() },
            confirmButton = confirmButton,
            dismissButton = dismissButton,
            modifier = modifier
        )
        return
    }

    val darkTheme = LocalExtendedColors.current.isDark
    val shape = animalHandDrawnShape(LocalDensity.current)
    Dialog(onDismissRequest = onDismissRequest) {
        // 留 12dp 透明边距：右上关闭钮要"飘"出 blob 角，同时防越出系统窗口被裁掉
        Box(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 6.dp,
                        shape = shape,
                        ambientColor = paint.cardShadow,
                        spotColor = paint.cardShadow
                    )
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 2.dp,
                        color = paint.componentBorder.copy(alpha = if (darkTheme) 0.7f else 1f),
                        shape = shape
                    )
                    .padding(start = 26.dp, end = 26.dp, top = 26.dp, bottom = 24.dp)
            ) {
                Text(
                    text = title,
                    style = ZhiLuType.pageTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                content()
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dismissButton != null) {
                        dismissButton()
                        Spacer(Modifier.width(12.dp))
                    }
                    confirmButton()
                }
            }
            // 关闭钮：飘在 blob 右上角外侧（参考仓库的"飘出式"），10dp 落在留白内、不越窗
            IconButton(
                onClick = onDismissRequest,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-10).dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
