package com.example.zhilu.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.example.zhilu.domain.model.Note
import com.example.zhilu.ui.component.NoteRow
import com.example.zhilu.ui.component.NoteRowVariant
import com.example.zhilu.ui.component.RevealAction
import com.example.zhilu.ui.component.RevealSide
import com.example.zhilu.ui.component.SwipeRevealRow
import com.example.zhilu.ui.theme.Spacing

/**
 * 首页笔记项。
 *
 * ## 交互逻辑
 *
 * | 手势 | 结果 |
 * |---|---|
 * | 右滑 | **露出**左侧「收藏 / 取消收藏」按钮 |
 * | 左滑 | **露出**右侧「删除」按钮 |
 * | 点按露出槽 | 才真正执行对应动作 |
 * | 长按 | 弹出等价的操作菜单（无障碍入口） |
 * | 上下滚动 | 行完全不响应；若已有行露出，滚动时自动收起 |
 *
 * 滑动**永远不会**直接改变笔记状态——这是防误触的关键：
 * 即使方向判定偶尔把一次斜向滚动认成横滑，用户最多看到一个按钮露出来，
 * 不会凭空多出一条收藏或一个删除确认框。
 *
 * 展开状态由列表层持有（[revealedSide]），因此天然互斥：同一时刻至多一行露出。
 */
@Composable
fun HomeNoteItem(
    note: Note,
    variant: NoteRowVariant,
    revealedSide: RevealSide,
    onRevealChange: (RevealSide) -> Unit,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    // 文档行铺满整宽，露出槽也跟着铺满；纸卡有外距与圆角，露出槽要对齐卡片。
    val revealInsets: PaddingValues
    val revealShape: Shape
    if (variant == NoteRowVariant.Document) {
        revealInsets = PaddingValues()
        revealShape = RectangleShape
    } else {
        revealInsets = PaddingValues(
            horizontal = Spacing.PageGutter,
            vertical = Spacing.CardGap / 2
        )
        revealShape = MaterialTheme.shapes.medium
    }

    val favoriteLabel = if (note.isFavorite) "取消收藏" else "收藏"
    val a11y = Modifier.semantics {
        customActions = listOf(
            CustomAccessibilityAction(favoriteLabel) { onToggleFavorite(); true },
            CustomAccessibilityAction("删除") { onDeleteRequest(); true }
        )
    }

    SwipeRevealRow(
        revealedSide = revealedSide,
        onRevealChange = onRevealChange,
        swipeRightAction = RevealAction(
            label = favoriteLabel,
            icon = if (note.isFavorite) Icons.Default.StarBorder else Icons.Default.Star,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onAction = {
                onRevealChange(RevealSide.None)
                onToggleFavorite()
            }
        ),
        swipeLeftAction = RevealAction(
            label = "删除",
            icon = Icons.Default.DeleteOutline,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            onAction = {
                onRevealChange(RevealSide.None)
                onDeleteRequest()
            }
        ),
        revealInsets = revealInsets,
        revealShape = revealShape
    ) {
        Box {
            NoteRow(
                note = note,
                variant = variant,
                // 无障碍自定义动作挂在行本身：滑动露出的两个操作对读屏用户不可见，
                // 必须另有一条等价的直达路径，否则「收藏/删除」就成了隐藏功能。
                modifier = a11y,
                // 已经露出按钮时，点正文先收起，避免「想关掉却进了笔记」。
                onClick = {
                    if (revealedSide != RevealSide.None) onRevealChange(RevealSide.None) else onClick()
                },
                onLongClick = { menuOpen = true },
                trailing = if (note.isFavorite) {
                    {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "已收藏",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                } else {
                    null
                }
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text(favoriteLabel) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (note.isFavorite) Icons.Default.StarBorder else Icons.Default.Star,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onToggleFavorite()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(text = "删除", color = MaterialTheme.colorScheme.error)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onDeleteRequest()
                    }
                )
            }
        }
    }
}
