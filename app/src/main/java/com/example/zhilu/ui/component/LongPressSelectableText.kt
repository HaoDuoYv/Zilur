package com.example.zhilu.ui.component

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/** 长按过程中手指允许的抖动（px）。超过就认为用户是想**划选区**，不弹菜单。 */
private const val LONG_PRESS_SLOP_PX = 12f

/**
 * 判定"长按不动"的窗口。
 *
 * 比平台长按超时（`ViewConfiguration.getLongPressTimeout()`，默认 500ms）**晚 100ms**：
 * 两者同时判的话，"长按后立刻拖"会**同时**弹出选区与菜单（真机两个一起出现）。
 * 多等这 0.1 秒，拖动位移就落进窗口内，菜单让位给选区。
 *
 * 不用 `ViewConfiguration.current`：那个 API 在当前 Compose 版本里不公开
 * （编译不过），而平台默认值本来就是 500ms。
 */
private const val LONG_PRESS_MENU_MS = 600L

/**
 * 只读态的**可选文本**容器：手势分三路，**顺序不能换**。
 *
 * 1. **点一下** → 顺着往上冒，交给祖先的 clickable（大纲展开、气泡点击等都靠它）；
 * 2. **长按后拖动** → 交给 [SelectionContainer] 划词（我们全程不消费）；
 * 3. **长按不动** → 回调 [onLongPress]，由调用方弹菜单。
 *
 * 走过的两条弯路，都别再走：
 * - **把菜单/长按挂在最外层**（父级 `detectTapGestures(onLongPress = …)`）：
 *   父级指针输入先于子级收到事件，外层一旦认领长按，`SelectionContainer` 的选区永远起不来
 *   —— 表现是"文字看着能选、实际选不动"。
 * - **用 `combinedClickable(onClick = {})` 挂菜单**：它的空 `onClick` 会把点击**消费掉**，
 *   祖先的 clickable 于是收不到，**点一下没反应**（真机实测：分支标题点不动、正文块点不开小节）。
 *   所以这里改用裸 `pointerInput`：只有"长按满且没动"这一种情况才消费事件。
 *
 * 菜单本身留给调用方（[onLongPress] 只给锚点位置）：笔记页的块菜单是"复制 / 引用到 AI"，
 * 助手页的消息菜单是"复制全文 / 引用"，条目不同但手势完全一样。
 *
 * @param onLongPress 长按判成"要菜单"时回调，参数是**按下位置**（让菜单贴着手指弹）
 */
@Composable
fun LongPressSelectableText(
    onLongPress: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.pointerInput(Unit) {
            // 一个手势一个循环：`awaitPointerEventScope` 里直接 `awaitPointerEvent`，
            // 不用 `awaitEachGesture` —— 后者是受限挂起作用域，里面再开 `awaitPointerEventScope`
            // 编译不过（"Restricted suspending functions…"）。
            awaitPointerEventScope {
                while (true) {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 在 Initial 通道上"只观察、不消费"：按住拖动时事件照常流向 SelectionContainer。
                    val stillPressed = withTimeoutOrNull(LONG_PRESS_MENU_MS) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.none { it.pressed }) return@withTimeoutOrNull false // 提前抬手 = 点按
                            // 判据用"相对 DOWN 的累计位移"，不是 `positionChange()`：
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
                    if (stillPressed != null) continue // 点按或划词：不消费，让事件往上冒

                    onLongPress(down.position)
                    // 长按判成"要菜单"，这次手势的 up 就不再上抛 —— 否则祖先的 clickable
                    // 会把一次长按当成点击（展开/收起会跟着乱跳）。
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val up = change.changedToUp()
                        change.consume()
                        if (up) break
                    }
                }
            }
        }
    ) {
        SelectionContainer {
            content()
        }
    }
}
