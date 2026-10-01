package com.example.zhilu.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.Note
import com.example.zhilu.ui.component.NoteRow
import com.example.zhilu.ui.component.NoteRowVariant
import com.example.zhilu.ui.theme.AlphaTokens
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 首页笔记项：右滑收藏、左滑删除（需确认）、长按弹出操作菜单。
 * 三个入口都复用同一批 ViewModel 方法，行为与此前一致。
 */
@Composable
fun HomeNoteItem(
    note: Note,
    variant: NoteRowVariant,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onToggleFavorite()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDeleteRequest()
                    false
                }
                else -> false
            }
        }
    )
    // 文档行铺满整宽，滑动手势底色不需要卡片外距；纸卡需要与卡片对齐。
    val swipeInset: Dp = when (variant) {
        NoteRowVariant.Document -> 0.dp
        NoteRowVariant.Card -> Spacing.PageGutter
    }
    // 只有纸卡才有圆角可言；全宽的文档行用平铺色带。
    val swipeShape: Shape = if (swipeInset == 0.dp) {
        RectangleShape
    } else {
        MaterialTheme.shapes.medium
    }

    Box {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                HomeSwipeBackground(
                    direction = dismissState.dismissDirection,
                    isFavorite = note.isFavorite,
                    horizontalInset = swipeInset,
                    shape = swipeShape
                )
            }
        ) {
            NoteRow(
                note = note,
                variant = variant,
                onClick = onClick,
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
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (note.isFavorite) "取消收藏" else "收藏") },
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

@Composable
private fun HomeSwipeBackground(
    direction: SwipeToDismissBoxValue,
    isFavorite: Boolean,
    horizontalInset: Dp,
    shape: Shape
) {
    val (color, label) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd ->
            MaterialTheme.colorScheme.tertiary to if (isFavorite) "取消收藏" else "收藏"
        SwipeToDismissBoxValue.EndToStart ->
            MaterialTheme.colorScheme.error to "删除"
        else -> return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalInset)
            .background(color.copy(alpha = AlphaTokens.Hover), shape),
        contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
            Alignment.CenterStart
        } else {
            Alignment.CenterEnd
        }
    ) {
        Text(
            text = label,
            style = ZhiLuType.chip,
            color = color,
            modifier = Modifier.padding(horizontal = Spacing.PageGutter)
        )
    }
}