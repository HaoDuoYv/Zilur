package com.example.zhilu.ui.note.knowledge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.KnowledgeCard
import com.example.zhilu.ui.theme.LocalExtendedColors
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.cardAccentColor
import com.example.zhilu.ui.theme.LocalThemePalette

/**
 * 页内目录（设计文档 §6.1）。
 *
 * 只在卡片数 ≥ 3 时由顶栏露出入口 —— 两个小节的笔记不需要目录，
 * 多一个图标只会稀释顶栏（"按需出现"）。
 *
 * 每一行是 `序号（卡片身份色）· 标题 · 小点数`；当前所在那一行带身份色竖条 + 7% 底色。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardOutlineSheet(
    cards: List<KnowledgeCard>,
    currentIndex: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val darkTheme = LocalExtendedColors.current.isDark

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "目录 · ${cards.size} 个小节",
                style = ZhiLuType.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)
            )
            cards.forEachIndexed { index, card ->
                val accent = cardAccentColor(card.accent, index, darkTheme, LocalThemePalette.current)
                val isCurrent = index == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(index) }
                        .background(
                            if (isCurrent) accent.copy(alpha = 0.07f) else Color.Transparent
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 当前所在：左缘身份色竖条
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(18.dp)
                            .background(
                                color = if (isCurrent) accent else Color.Transparent,
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = (index + 1).toString().padStart(2, '0'),
                        style = ZhiLuType.cardTitle,
                        color = accent
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = card.title.ifBlank { "未命名小节" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = card.blocks.count { it.parentBranchId == null }.toString(),
                        style = ZhiLuType.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
