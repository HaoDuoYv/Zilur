package com.example.zhilu.ui.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.Note
import com.example.zhilu.ui.component.AppIconButton
import com.example.zhilu.ui.component.DocumentRow
import com.example.zhilu.ui.component.MetaLine
import com.example.zhilu.ui.component.rememberTagAccent
import com.example.zhilu.ui.home.formatTime
import com.example.zhilu.ui.theme.AlphaTokens
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

private const val TRASH_RETENTION_DAYS = 30L

/**
 * 回收站行：左滑恢复、右滑请求永久删除（需二次确认），⋮ 菜单作为无障碍备选入口。
 * 与笔记列表同为文档行，书脊取中性灰——已删除的内容不该再显得鲜活。
 */
@Composable
fun TrashRow(
    note: Note,
    onRestore: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onRestore()
                    true
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDeleteRequest()
                    false
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeBackground(dismissState.dismissDirection) }
    ) {
        DocumentRow(accent = rememberTagAccent(tagColor = null)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = note.title.ifBlank { "未命名知识" },
                        style = ZhiLuType.rowTitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    MetaLine(
                        parts = listOf("删除于 ${formatTime(note.updatedAt)}", remainingText(note)),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Box {
                    AppIconButton(
                        icon = Icons.Default.MoreVert,
                        contentDescription = "更多操作",
                        onClick = { menuOpen = true }
                    )
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("恢复") },
                            leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onRestore()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "永久删除",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
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
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val (color, label) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.tertiary to "恢复"
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error to "永久删除"
        else -> return
    }
    // 文档行没有卡片圆角，底色因此铺满整宽，不做形状。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color.copy(alpha = AlphaTokens.Hover)),
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

private fun remainingText(note: Note): String {
    val deletedAt = note.deletedAt ?: return ""
    val elapsedDays = (System.currentTimeMillis() - deletedAt) / 86_400_000L
    val remaining = (TRASH_RETENTION_DAYS - elapsedDays).coerceAtLeast(0L)
    return "剩余 $remaining 天"
}