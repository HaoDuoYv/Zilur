package com.example.zhilu.ui.trash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.example.zhilu.domain.model.Note
import com.example.zhilu.ui.component.AppIconButton
import com.example.zhilu.ui.component.DocumentRow
import com.example.zhilu.ui.component.MetaLine
import com.example.zhilu.ui.component.RevealAction
import com.example.zhilu.ui.component.RevealSide
import com.example.zhilu.ui.component.SwipeRevealRow
import com.example.zhilu.ui.component.rememberTagAccent
import com.example.zhilu.ui.home.formatTime
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

private const val TRASH_RETENTION_DAYS = 30L

/**
 * 回收站行：右滑**露出**「恢复」按钮、左滑**露出**「永久删除」按钮，点按才执行；
 * ⋮ 菜单作为无障碍备选入口。
 *
 * 与首页笔记行同一套防误触约定：滑动本身不改变任何状态，只把操作槽推到台面上。
 * 与笔记列表同为文档行，书脊取中性灰——已删除的内容不该再显得鲜活。
 */
@Composable
fun TrashRow(
    note: Note,
    revealedSide: RevealSide,
    onRevealChange: (RevealSide) -> Unit,
    onRestore: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val a11y = Modifier.semantics {
        customActions = listOf(
            CustomAccessibilityAction("恢复") { onRestore(); true },
            CustomAccessibilityAction("永久删除") { onDeleteRequest(); true }
        )
    }

    SwipeRevealRow(
        revealedSide = revealedSide,
        onRevealChange = onRevealChange,
        swipeRightAction = RevealAction(
            label = "恢复",
            icon = Icons.Default.Restore,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onAction = {
                onRevealChange(RevealSide.None)
                onRestore()
            }
        ),
        swipeLeftAction = RevealAction(
            label = "永久删除",
            icon = Icons.Default.DeleteForever,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            onAction = {
                onRevealChange(RevealSide.None)
                onDeleteRequest()
            }
        )
    ) {
        DocumentRow(
            accent = rememberTagAccent(tagColor = null),
            modifier = a11y,
            onClick = {
                if (revealedSide != RevealSide.None) onRevealChange(RevealSide.None)
            },
            onLongClick = { menuOpen = true }
        ) {
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
                        modifier = Modifier.padding(top = Spacing.RowGapTight)
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

private fun remainingText(note: Note): String {
    val deletedAt = note.deletedAt ?: return ""
    val elapsedDays = (System.currentTimeMillis() - deletedAt) / 86_400_000L
    val remaining = (TRASH_RETENTION_DAYS - elapsedDays).coerceAtLeast(0L)
    return "剩余 $remaining 天"
}
