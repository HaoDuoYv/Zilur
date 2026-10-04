package com.example.zhilu.ui.note.blocks

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 长按多久算"要菜单"。
 *
 * **必须比平台的 500ms 稍晚一点**：`SelectionContainer` 也在 500ms 那一刻起步，
 * 如果两者同时判，用户"长按后立刻拖"就会同时触发选区与菜单（真机实测两个一起弹）。
 * 多等这 100ms，拖动的位移就能在本窗口内被看到，于是菜单主动让位给选区。
 * 代价只是"纯长按"的菜单晚 0.1 秒出现，感知不到。
 */
private const val LONG_PRESS_MENU_MS = 600L

/** 长按过程中手指允许的抖动（px）。超过就认为用户是想**划选区**，不再弹菜单。 */
private const val LONG_PRESS_SLOP_PX = 12f

/**
 * 只读态的**可选文本** + 块级菜单。
 *
 * 为什么长按要自己算，而不是直接给父 Box 挂 `detectTapGestures(onLongPress = …)`：
 * [SelectionContainer] 也靠长按起步（它要跟随手指拖出选区），而父级指针输入**先于**子级收到事件 ——
 * 外层一旦认领长按，选区永远起不来，表现就是"文字看着能选、实际选不动"。
 *
 * 所以两个动作放在**同一层**，按长按之后手指有没有移动来分流：
 * - 没动 → 用户要的是菜单（保留老行为：复制此块 / 引用到 AI）；
 * - 拖了 → 不消费事件，交给 [SelectionContainer] 划选区。
 *
 * 已知代价：选区已经存在时，再长按别处会先被 [SelectionContainer] 接管、菜单不弹；
 * 点一下清掉选区即可恢复。比"二选一砍掉一个入口"划算。
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

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                // 在 Initial 通道上"只观察、不消费"：按住拖动时事件照常流向 SelectionContainer。
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val longPressedStill = withTimeoutOrNull(LONG_PRESS_MENU_MS) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.none { it.pressed }) return@withTimeoutOrNull false // 提前抬手 = 点按
                        // 判据用"相对 DOWN 的累计位移"而不是 `positionChange()`：
                        // 后者每帧只给"相对上一帧"的位移，手指匀速拖动时单帧位移很小，
                        // 拿它跟 slop 比会漏判成"没动"，菜单就把选区打断了。
                        val drift = event.changes
                            .filter { it.pressed }
                            .maxOfOrNull { (it.position - down.position).getDistance() }
                            ?: 0f
                        if (drift > LONG_PRESS_SLOP_PX) return@withTimeoutOrNull false // 在拖 = 划选区
                    }
                    @Suppress("UNREACHABLE_CODE") false
                }
                // withTimeoutOrNull 返回 null 才代表"满 500ms 且没动过"
                if (longPressedStill == null) {
                    menuAnchor = down.position
                    menuExpanded = true
                }
            }
        }
    ) {
        SelectionContainer {
            content()
        }
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
