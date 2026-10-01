package com.example.zhilu.ui.component

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.LocalReducedMotion
import com.example.zhilu.ui.theme.MotionSpring
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 滑动露出侧的受控状态。 */
enum class RevealSide {
    /** 未展开。 */
    None,

    /** 露出左侧槽（内容右移，即「右滑」触发）。 */
    Leading,

    /** 露出右侧槽（内容左移，即「左滑」触发）。 */
    Trailing
}

/**
 * 列表的露出态：同一时刻至多一行露出，并记录露出的是哪一侧。
 *
 * 由列表层持有，用于实现「单行互斥」与「滚动/切页统一收起」。
 * `null` 表示当前没有任何行处于露出态。
 */
data class RowReveal(val id: Long, val side: RevealSide)

/**
 * 露出槽里的一个操作。
 *
 * 注意：[onAction] 只会在用户**点按**时执行——滑动本身永远不具备执行能力，
 * 这是本组件防误触的核心约定。请勿把 [onAction] 接到任何手势回调上。
 */
class RevealAction(
    val label: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val onAction: () -> Unit
)

/** 露出槽宽度：容得下「取消收藏」「永久删除」这类四字文案。 */
private val RevealWidth = 92.dp

/** 松手后吸附展开的最小位移比例；低于此值回弹归零。 */
private const val SettleFraction = 0.35f

/**
 * 方向锁定的严格程度：横向累计位移必须超过纵向的 [LockBias] 倍才判定为横滑。
 *
 * Compose 自带的判定只要求「主轴位移 > 交叉轴位移」（1 倍），
 * 手指在上下滚动时只要略微偏斜就可能被判成横滑。取 1.6 倍后，
 * 只有意图明确的横向滑动才会劫持手势，斜向滚动仍然归列表。
 */
private const val LockBias = 1.6f

/**
 * 滑动露出操作槽的列表行容器。
 *
 * ## 与 [androidx.compose.material3.SwipeToDismissBox] 的区别
 *
 * | | SwipeToDismissBox | SwipeRevealRow |
 * |---|---|---|
 * | 方向判定 | 主轴位移 > 交叉轴位移即锁定 | 横向必须 > 纵向 1.6 倍 |
 * | 滑到底 | **立即执行动作** | 只露出按钮，不执行 |
 * | 误触后果 | 收藏被静默切换 / 弹出删除确认 | 什么都不发生 |
 *
 * ## 交互契约
 *
 * - 展开状态由调用方持有（[revealedSide]），因此天然支持「同时只允许一行展开」与
 *   「列表滚动时统一收起」；本组件不自行 `remember` 展开状态。
 * - [RevealAction.onAction] 仅在用户点按露出槽时调用，返回后由调用方负责收起
 *   （把 [revealedSide] 置回 [RevealSide.None]）。
 * - 点按正文请由调用方在展开时先行收起，避免「点一下正文却打开了笔记」。
 *
 * @param revealedSide 当前展开侧。
 * @param onRevealChange 吸附结束后的新展开侧；调用方据此更新状态。
 * @param swipeRightAction 右滑时露出的**左侧**槽。
 * @param swipeLeftAction 左滑时露出的**右侧**槽。
 * @param containerColor 内容层底色。必须不透明，否则未拖动时露出槽会透出来。
 * @param revealInsets 露出槽相对整行的内缩，用于让色带与卡片对齐。
 * @param revealShape 露出槽的形状；配合 [revealInsets] 使用。
 */
@Composable
fun SwipeRevealRow(
    revealedSide: RevealSide,
    onRevealChange: (RevealSide) -> Unit,
    modifier: Modifier = Modifier,
    swipeRightAction: RevealAction? = null,
    swipeLeftAction: RevealAction? = null,
    containerColor: Color = MaterialTheme.colorScheme.background,
    revealInsets: PaddingValues = PaddingValues(),
    revealShape: Shape = RectangleShape,
    gestureEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val revealPx = with(LocalDensity.current) { RevealWidth.toPx() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val settleSpec: AnimationSpec<Float> = if (LocalReducedMotion.current) {
        snap()
    } else {
        MotionSpring.Move
    }

    // 水平偏移（px）。拖动期间直接写状态，松手后由动画补齐——
    // 手势回调跑在 AwaitPointerEventScope 这个受限挂起作用域里，不能直接调用
    // Animatable.snapTo 之类的挂起函数，所以这里用普通状态 + 外部动画任务。
    var offsetPx by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var dragging by remember { mutableStateOf(false) }

    // 手势块只在 revealPx / 两侧可用性变化时重建，回调与当前展开态必须走
    // rememberUpdatedState，否则会一直用到首次组合时的旧值。
    val currentRevealChange by rememberUpdatedState(onRevealChange)
    val currentRevealedSide by rememberUpdatedState(revealedSide)
    val currentSettleSpec by rememberUpdatedState(settleSpec)

    val canSwipeRight = swipeRightAction != null
    val canSwipeLeft = swipeLeftAction != null

    fun targetOffset(side: RevealSide): Float = when {
        side == RevealSide.Leading && canSwipeRight -> revealPx
        side == RevealSide.Trailing && canSwipeLeft -> -revealPx
        else -> 0f
    }

    /** 非挂起：可在受限挂起作用域里安全调用。 */
    fun animateTo(target: Float) {
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(offsetPx, target, animationSpec = currentSettleSpec) { value, _ -> offsetPx = value }
        }
    }

    // 外部状态（互斥收起 / 滚动收起 / 动作执行后归位）驱动视觉。
    LaunchedEffect(revealedSide, revealPx) {
        if (dragging) return@LaunchedEffect
        val target = targetOffset(revealedSide)
        if (offsetPx != target) animateTo(target)
    }

    val gestureModifier = if (!gestureEnabled || (!canSwipeRight && !canSwipeLeft)) {
        Modifier
    } else {
        Modifier.pointerInput(revealPx, canSwipeRight, canSwipeLeft) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)

                // ── 阶段一：方向判定。锁定前不消费任何事件，
                //    纵向手势原样交给 LazyColumn，行完全不响应。
                var accumulated = Offset.Zero
                var lockedHorizontal: Boolean? = null
                while (lockedHorizontal == null) {
                    val event = awaitPointerEvent()
                    val move = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (move.isConsumed || !move.pressed) break
                    accumulated += move.positionChange()
                    val horizontal = abs(accumulated.x)
                    val vertical = abs(accumulated.y)
                    when {
                        horizontal > slop && horizontal > vertical * LockBias -> lockedHorizontal = true
                        vertical > slop -> lockedHorizontal = false
                    }
                }
                if (lockedHorizontal != true) return@awaitEachGesture

                // ── 阶段二：拖动。只改变水平偏移，不做任何状态变更。
                dragging = true
                settleJob?.cancel()
                val lowerBound = if (canSwipeLeft) -revealPx else 0f
                val upperBound = if (canSwipeRight) revealPx else 0f
                while (true) {
                    val event = awaitPointerEvent()
                    val move = event.changes.firstOrNull { it.id == down.id } ?: break
                    val delta = move.positionChange().x
                    if (delta != 0f) {
                        offsetPx = (offsetPx + delta).coerceIn(lowerBound, upperBound)
                    }
                    move.consume()
                    if (!move.pressed) break
                }
                dragging = false

                // ── 阶段三：吸附。越线只代表「露出」，动作必须由用户点按触发。
                val settled = when {
                    offsetPx > revealPx * SettleFraction -> RevealSide.Leading
                    offsetPx < -revealPx * SettleFraction -> RevealSide.Trailing
                    else -> RevealSide.None
                }
                val resolved = if (targetOffset(settled) == 0f) RevealSide.None else settled
                animateTo(targetOffset(resolved))
                if (resolved != currentRevealedSide) currentRevealChange(resolved)
                if (resolved != RevealSide.None) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
    }

    Box(modifier = modifier.clipToBounds().then(gestureModifier)) {
        // 露出槽层：被内容层完全遮住，只有拖动后才会显形。
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(revealInsets)
                .clip(revealShape)
        ) {
            if (swipeRightAction != null) {
                RevealSlot(
                    action = swipeRightAction,
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(RevealWidth)
                        .background(swipeRightAction.containerColor)
                )
            }
            Spacer(Modifier.weight(1f))
            if (swipeLeftAction != null) {
                RevealSlot(
                    action = swipeLeftAction,
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(RevealWidth)
                        .background(swipeLeftAction.containerColor)
                )
            }
        }

        // 内容层：不透明底色，拖动时才会让出下方的槽位。
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .background(containerColor)
        ) {
            content()
        }
    }
}

@Composable
private fun RevealSlot(
    action: RevealAction,
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(role = Role.Button) { action.onAction() }
            .padding(horizontal = Spacing.Sm, vertical = Spacing.Sm),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = action.contentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(Spacing.Xs))
        Text(
            text = action.label,
            style = ZhiLuType.chip,
            color = action.contentColor,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
