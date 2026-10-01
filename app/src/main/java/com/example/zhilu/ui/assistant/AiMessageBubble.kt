package com.example.zhilu.ui.assistant

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.zhilu.domain.model.AiMessage
import com.example.zhilu.domain.model.AiRole
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiMessageBubble(
    message: AiMessage,
    isStreaming: Boolean
) {
    when (message.role) {
        AiRole.TOOL -> ToolCallBadge(message = message)
        else -> ChatBubble(message = message, isStreaming = isStreaming)
    }
}

@Composable
private fun ToolCallBadge(message: AiMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    modifier = Modifier
                        .height(12.dp)
                        .width(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(message: AiMessage, isStreaming: Boolean) {
    val isUser = message.role == AiRole.USER
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp
    )
    // 用户气泡用降饱和的墨蓝容器色，避免满饱和实底过于刺眼。
    val background = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val hasText = message.content.isNotBlank() || isStreaming
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.PageGutter, vertical = Spacing.Xs),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(shape)
                .background(background)
                .then(
                    if (isUser) {
                        Modifier
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    }
                )
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        clipboard.setText(AnnotatedString(message.content))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    }
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            message.images.forEach { uri ->
                SubcomposeAsyncImage(
                    model = uri,
                    contentDescription = "图片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(240.dp)
                        .height(160.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
            if (hasText) {
                if (isUser) {
                    // 用户消息保持纯文本（原样回显）
                    Text(
                        text = message.content + if (isStreaming) "▍" else "",
                        style = ZhiLuType.bodySmall,
                        color = textColor,
                        modifier = Modifier.padding(top = if (message.images.isNotEmpty()) 6.dp else 0.dp)
                    )
                } else {
                    // AI 消息富文本渲染：Markdown + LaTeX + 代码块
                    AiMessageContent(
                        text = message.content + if (isStreaming) "▍" else "",
                        textColor = textColor,
                        modifier = Modifier.padding(top = if (message.images.isNotEmpty()) 6.dp else 0.dp)
                    )
                }
            }
        }
    }
}
