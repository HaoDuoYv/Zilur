package com.example.zhilu.ui.note.blocks

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import com.example.zhilu.ui.component.LongPressSelectableText

/**
 * 只读态的**可选文本** + 块级菜单（复制 / 引用到 AI）。
 *
 * 手势三路（点按上冒 / 长按拖动划词 / 长按不动弹菜单）的判定全部在
 * [LongPressSelectableText] 里，这里只负责这个页面的菜单条目与锚点。
 */
@Composable
fun SelectableBlockText(
    menuLabel: String,
    onCopyBlock: () -> Unit,
    onCiteToAi: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    // 菜单贴着按下位置弹，否则长按屏幕下半部分时它会跑到老远
    var menuAnchor by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    LongPressSelectableText(
        modifier = modifier,
        onLongPress = { position ->
            menuAnchor = position
            menuExpanded = true
        }
    ) {
        content()
    }

    DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false },
        offset = with(density) { DpOffset(menuAnchor.x.toDp(), menuAnchor.y.toDp()) }
    ) {
        DropdownMenuItem(
            text = { Text(menuLabel) },
            onClick = {
                menuExpanded = false
                onCopyBlock()
            }
        )
        DropdownMenuItem(
            text = { Text("引用到 AI") },
            onClick = {
                menuExpanded = false
                onCiteToAi()
            }
        )
    }
}
