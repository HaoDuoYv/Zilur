package com.example.zhilu.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.MotionDuration
import com.example.zhilu.ui.theme.LocalReducedMotion
import kotlin.math.roundToInt

/** 抽屉占屏宽的比例（参考主流对话应用：约 3/4，右侧留一条能看见原界面）。 */
private const val DefaultWidthFraction = 0.75f

/** 遮罩最深的不透明度。 */
private const val ScrimAlpha = 0.45f

/** 关闭状态下左缘的滑动开抽屉热区。 */
private val EdgeSwipeWidth = 28.dp

/** 边缘滑开所需的最小位移。 */
private val EdgeSwipeThreshold = 48.dp

/**
 * 推开式抽屉：抽屉从左侧推入，**原界面被整体推到右侧并压暗**。
 *
 * 为什么不用 Material3 的 `ModalNavigationDrawer`：
 * 它只在原界面上盖一层遮罩，界面本身不动；而"内容被推开、右侧留一条能看出是什么页面"
 * 是这种抽屉的识别特征（也是参考图里的样子）。另外它的宽度会跟着内容收缩
 * （`sizeIn(max = 360.dp)` + 内容自身宽度），放不进"固定 3/4 屏宽"这个要求。
 *
 * 交互：点 ☰ 打开 · 点右侧灰色区域关闭 · 系统返回键关闭 · 关闭时从左缘右滑也能打开。
 */
@Composable
fun PushDrawer(
    open: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    widthFraction: Float = DefaultWidthFraction,
    drawer: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    val progress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else MotionDuration.Medium
        ),
        label = "push_drawer_progress"
    )

    // 打开时把返回键收归自己，避免直接退出页面
    BackHandler(enabled = open, onBack = onClose)

    val density = LocalDensity.current
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val drawerWidthPx = containerWidthPx * widthFraction
    val edgeSwipeThresholdPx = with(density) { EdgeSwipeThreshold.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerWidthPx = it.width.toFloat() }
    ) {
        // 1. 被推开的内容
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset((drawerWidthPx * progress).roundToInt(), 0) }
        ) {
            content()
        }

        // 2. 压暗层：整屏铺满，点它关闭。画在抽屉下面，所以不会挡住抽屉里的点击。
        if (progress > 0.01f) {
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = ScrimAlpha * progress))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClose
                    )
            )
        }

        // 3. 抽屉本体：从左侧滑入
        if (progress > 0.01f || open) {
            Box(
                modifier = Modifier
                    .width(with(density) { drawerWidthPx.toDp() })
                    .fillMaxHeight()
                    .offset { IntOffset((-drawerWidthPx * (1f - progress)).roundToInt(), 0) }
            ) {
                drawer()
            }
        }

        // 4. 关闭状态下的左缘热区：右滑打开
        if (!open && progress < 0.01f) {
            var accumulated by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(EdgeSwipeWidth)
                    .fillMaxHeight()
                    .pointerInput(edgeSwipeThresholdPx) {
                        detectHorizontalDragGestures(
                            onDragStart = { accumulated = 0f },
                            onDragEnd = {
                                if (accumulated > edgeSwipeThresholdPx) onOpen()
                                accumulated = 0f
                            },
                            onDragCancel = { accumulated = 0f }
                        ) { _, dragAmount ->
                            accumulated += dragAmount
                        }
                    }
            )
        }
    }
}
