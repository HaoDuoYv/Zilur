package com.example.zhilu.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.AiConversation
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 历史对话抽屉（左侧拉出）。
 *
 * 结构参照主流对话应用：最上面一条「新建对话」，下面是**按时间分组**的历史
 * （今天 / 7 天内 / 30 天内 / 更早按月，见 [groupConversations]），
 * 当前所在的会话高亮。
 */
@Composable
fun ConversationHistoryDrawer(
    conversations: List<AiConversation>,
    currentConversationId: Long?,
    onNewConversation: () -> Unit,
    onSelectConversation: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = remember(conversations) { groupConversations(conversations) }

    ModalDrawerSheet(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "历史对话",
                style = ZhiLuType.rowTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    start = Spacing.Md,
                    end = Spacing.Md,
                    top = Spacing.Md,
                    bottom = Spacing.Sm
                )
            )

            DrawerRow(
                text = "新建对话",
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                emphasised = true,
                onClick = onNewConversation
            )

            if (groups.isEmpty()) {
                Text(
                    text = "还没有历史对话",
                    style = ZhiLuType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.Md, vertical = Spacing.Md)
                )
                return@Column
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                groups.forEach { group ->
                    item(key = "group-${group.label}") {
                        Text(
                            text = group.label,
                            style = ZhiLuType.meta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = Spacing.Md,
                                end = Spacing.Md,
                                top = Spacing.Md,
                                bottom = Spacing.Xs
                            )
                        )
                    }
                    items(group.conversations, key = { it.id }) { conversation ->
                        DrawerRow(
                            text = conversation.title.ifBlank { "新对话" },
                            selected = conversation.id == currentConversationId,
                            onClick = { onSelectConversation(conversation.id) },
                            trailing = {
                                IconButton(
                                    onClick = { onDeleteConversation(conversation.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "删除对话",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(Spacing.Lg)) }
            }
        }
    }
}

@Composable
private fun DrawerRow(
    text: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    emphasised: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Sm, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                }
            )
            // 关掉水波纹：列表里滑动时误触高亮比"点中了哪一条"更干扰
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = Spacing.Sm, vertical = Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(modifier = Modifier.width(Spacing.Sm))
        }
        Text(
            text = text,
            style = ZhiLuType.body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = when {
                selected -> MaterialTheme.colorScheme.primary
                emphasised -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}
