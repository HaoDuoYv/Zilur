package com.example.zhilu.ui.assistant

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.AiConversation
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.theme.ZhiLuType

/** AI 助手顶栏：标题 + 历史会话下拉（新建 / 切换 / 删除）。作为一级页不再有返回键。 */
@Composable
fun AssistantTopBar(
    conversations: List<AiConversation>,
    onNewConversation: () -> Unit,
    onSelectConversation: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    AppTopBar(
        title = "AI 助手",
        actions = {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "历史对话",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("新建对话") },
                    onClick = {
                        onNewConversation()
                        menuOpen = false
                    }
                )
                if (conversations.isNotEmpty()) {
                    HorizontalDivider()
                    conversations.forEach { conversation ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = conversation.title,
                                    style = ZhiLuType.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            onClick = {
                                onSelectConversation(conversation.id)
                                menuOpen = false
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onDeleteConversation(conversation.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "删除会话",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    )
}