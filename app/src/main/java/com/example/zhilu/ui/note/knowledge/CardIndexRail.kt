package com.example.zhilu.ui.note.knowledge

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.KnowledgeCard
import com.example.zhilu.ui.theme.AlphaTokens
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.LocalExtendedColors
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.cardAccentColor
import com.example.zhilu.ui.theme.LocalThemePalette
import kotlinx.coroutines.delay

/** 命中区宽度。刻意 ≤ 24dp：再宽就会跟列表的纵向滚动抢手势（§6.2）。 */
private val RailHitWidth = 24.dp

/** 卡片数到这个数才出现 —— 两个小节不需要索引轨。 */
private const val MinCardsForRail = 4

/** 静置多久淡出。 */
private const val IdleFadeMillis = 2500L

/**
 * 右缘索引轨（设计文档 §6.2）。
 *
 * 一个刻度 = 一张卡片：颜色是卡片身份色、长度 ∝ 小点数、当前可视卡片加粗加长。
 * 按住上下拖动即连续滑过卡片（`onJump` 由调用方落到 `scrollToItem`），
 * 拖动时右侧浮现 `01 惯性标题` 气泡；点按单个刻度直接跳转。
 *
 * **按需出现**：卡片数 ≥ 4 才渲染；打开时先露一下，之后"滚动/拖动时淡入、静置 2.5 秒淡出"。
 * 淡出后**连手势层一起撤掉**，否则一条看不见的 24dp 竖条会持续吃掉右缘的点击与滚动。
 */
@Composable
fun CardIndexRail(
    cards: List<KnowledgeCard>,
    currentIndex: Int,
    scrollActive: Boolean,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (cards.size < MinCardsForRail) return

    val darkTheme = LocalExtendedColors.current.isDark
    val density = LocalDensity.current
    val tickGap = 4.dp

    // 刻度长度 ∝ 顶层小点数：长卡更长，扫视时能看出哪一节内容多
    val heights = cards.map { card ->
        val count = card.blocks.count { it.parentBranchId == null }
        (11 + count.coerceIn(0, 7)).dp
    }
    val slotPx = remember(heights, density) {
        heights.map { with(density) { (it + tickGap).toPx() } }
    }
    val topsPx = remember(slotPx) {
        val tops = ArrayList<Float>(slotPx.size)
        var acc = 0f
        for (slot in slotPx) {
            tops += acc
            acc += slot
        }
        tops
    }

    var interacting by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(false) }
    var bubbleIndex by remember { mutableStateOf<Int?>(null) }

    // 打开时先露一下，让用户知道右缘有这个设施
    LaunchedEffect(Unit) {
        active = true
        delay(IdleFadeMillis)
        if (!interacting && !scrollActive) active = false
    }
    LaunchedEffect(scrollActive, interacting, cards.size) {
        if (scrollActive || interacting) {
            active = true
        } else {
            delay(IdleFadeMillis)
            active = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (active || interacting) 1f else 0f,
        label = "CardIndexRailAlpha"
    )

    fun indexAt(y: Float): Int {
        var result = 0
        for (index in topsPx.indices) {
            if (y >= topsPx[index]) result = index
        }
        return result.coerceIn(0, cards.lastIndex)
    }

    Box(
        modifier = modifier
            .width(RailHitWidth)
            .alpha(alpha),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(tickGap)
        ) {
            cards.forEachIndexed { index, card ->
                val accent = cardAccentColor(card.accent, index, darkTheme, LocalThemePalette.current)
                val isCurrent = index == currentIndex
                Box(
                    modifier = Modifier
                        .width(if (isCurrent) 5.dp else 3.dp)
                        .height(heights[index])
                        .background(
                            color = accent.copy(alpha = if (isCurrent) 1f else AlphaTokens.Overlay),
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }
        }

        // 手势层只在可见时存在（见 KDoc）
        if (active || interacting) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(cards.size) {
                        detectDragGestures(
                            onDragStart = { position ->
                                interacting = true
                                val index = indexAt(position.y)
                                bubbleIndex = index
                                onJump(index)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val index = indexAt(change.position.y)
                                if (index != bubbleIndex) {
                                    bubbleIndex = index
                                    onJump(index)
                                }
                            },
                            onDragEnd = {
                                interacting = false
                                bubbleIndex = null
                            },
                            onDragCancel = {
                                interacting = false
                                bubbleIndex = null
                            }
                        )
                    }
                    .pointerInput(cards.size) {
                        detectTapGestures { position -> onJump(indexAt(position.y)) }
                    }
            )
        }

        val bubble = bubbleIndex
        if (bubble != null && bubble in cards.indices) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = RailHitWidth + 6.dp),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.inverseSurface
            ) {
                Text(
                    text = "${(bubble + 1).toString().padStart(2, '0')} " +
                        cards[bubble].title.ifBlank { "未命名小节" },
                    style = ZhiLuType.meta,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
        }
    }
}
