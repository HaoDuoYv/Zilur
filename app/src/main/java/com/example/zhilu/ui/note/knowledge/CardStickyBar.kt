package com.example.zhilu.ui.note.knowledge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.KnowledgeCard
import com.example.zhilu.ui.component.ElevationTokens
import com.example.zhilu.ui.component.ZhiLuDivider
import com.example.zhilu.ui.theme.LocalExtendedColors
import com.example.zhilu.ui.theme.LocalReducedMotion
import com.example.zhilu.ui.theme.MotionDuration
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.cardAccentColor
import com.example.zhilu.ui.theme.motionEnterTween
import com.example.zhilu.ui.theme.motionExitTween

/** 卡片头滚出屏幕多少距离后才浮出提示条（大约一个卡片头的高度）。 */
val CardStickyThreshold = 56.dp

/**
 * 「我在第几章」提示条（设计文档 §6.3）。
 *
 * ## 为什么不是真正的 `stickyHeader`
 *
 * 真 `stickyHeader` 要求卡片头成为一个独立的 LazyColumn item，也就是说
 * **必须把卡片拆成"头 + 正文"两个 item**。那样会连带打断：卡片 Surface 的整体性（§5.5 要求
 * 卡片仍是一张白纸）、卡片的滑动/长按手势边界、以及 `key` 与拖拽/粘贴的状态绑定。
 * 代价明显大于收益，所以这里用**浮出式等价物**：当前卡片的头滚出视口后，
 * 在列表顶部浮出一条只读的 `序号 + 标题 + 小点数`（白底 + 底部发丝线 + 轻微阴影，
 * 与首页 `HomeNoteList` 的 stickyHeader 视觉一致），点它回到该小节顶部。
 *
 * 它不承接滚动/拖拽手势（只有 clickable），宽度铺满，视觉上就是一条吸附条。
 */
@Composable
fun CardStickyBar(
    cards: List<KnowledgeCard>,
    currentIndex: Int,
    visible: Boolean,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val card = cards.getOrNull(currentIndex) ?: return
    val darkTheme = LocalExtendedColors.current.isDark
    val reducedMotion = LocalReducedMotion.current
    val accent = cardAccentColor(card.accent, currentIndex, darkTheme)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motionEnterTween(MotionDuration.Short, enabled = !reducedMotion)),
        exit = fadeOut(motionExitTween(MotionDuration.Short, enabled = !reducedMotion)),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onJump(currentIndex) },
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = ElevationTokens.Raised
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = (currentIndex + 1).toString().padStart(2, '0'),
                        style = ZhiLuType.cardTitle,
                        color = accent
                    )
                    Spacer(modifier = Modifier.width(9.dp))
                    Text(
                        text = card.title.ifBlank { "未命名小节" },
                        style = ZhiLuType.rowTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${card.blocks.count { it.parentBranchId == null }} 点",
                        style = ZhiLuType.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ZhiLuDivider()
            }
        }
    }
}
