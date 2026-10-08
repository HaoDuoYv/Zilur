package com.example.zhilu.ui.assistant

import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.zhilu.ai.toolNameLabel
import com.example.zhilu.domain.model.AiMessage
import com.example.zhilu.domain.model.AiRole
import com.example.zhilu.ui.component.LongPressSelectableText
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 一条消息气泡。
 *
 * @param quotedMessage 本条消息「引用（回复）」的那条消息；null = 不是引用消息
 * @param onQuote 长按菜单点「引用」时的回调；null = 不提供引用入口（流式合成气泡等）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiMessageBubble(
    message: AiMessage,
    isStreaming: Boolean,
    isActiveTool: Boolean = false,
    quotedMessage: AiMessage? = null,
    onQuote: ((AiMessage) -> Unit)? = null
) {
    when (message.role) {
        AiRole.TOOL -> ToolCallBadge(message = message, isActive = isActiveTool)
        else -> ChatBubble(
            message = message,
            isStreaming = isStreaming,
            quotedMessage = quotedMessage,
            onQuote = onQuote
        )
    }
}

@Composable
private fun ToolCallBadge(message: AiMessage, isActive: Boolean) {
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
                if (isActive) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = message.toolName?.let { toolNameLabel(it) } ?: message.content,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatBubble(
    message: AiMessage,
    isStreaming: Boolean,
    quotedMessage: AiMessage?,
    onQuote: ((AiMessage) -> Unit)?
) {
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

    // 长按菜单：贴着手指弹（锚点就是按下位置）。
    var menuExpanded by remember { mutableStateOf(false) }
    var menuAnchor by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.PageGutter, vertical = Spacing.Xs),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        // 外层 Box 紧贴气泡：菜单的锚点坐标系与手指位置的坐标系因此重合
        // （直接挂在整行 Row 上的话，右侧用户气泡的菜单会横着飘出去几百 dp）。
        Box {
            LongPressSelectableText(
                onLongPress = { position ->
                    menuAnchor = position
                    menuExpanded = true
                }
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
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // 引用（回复）的消息：先亮出「在说哪条」，再看本条内容。
                    if (quotedMessage != null) {
                        QuotedMessageBlock(
                            message = quotedMessage,
                            textColor = textColor,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    // 用户气泡顶部回放「当时引用了哪些知识内容」，让它和 AI 的回答对得上号。
                    if (isUser && message.refs.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            message.refs.forEach { ref ->
                                RefChip(ref = ref)
                            }
                        }
                    }
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
                        when {
                            isUser -> Text(
                                text = message.content,
                                style = ZhiLuType.bodySmall,
                                color = textColor,
                                modifier = Modifier.padding(top = if (message.images.isNotEmpty()) 6.dp else 0.dp)
                            )
                            message.content.isBlank() && isStreaming -> TypingIndicator(color = textColor)
                            else -> AiMessageContent(
                                text = message.content + if (isStreaming) "▍" else "",
                                textColor = textColor,
                                modifier = Modifier.padding(top = if (message.images.isNotEmpty()) 6.dp else 0.dp)
                            )
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                offset = with(density) { DpOffset(menuAnchor.x.toDp(), menuAnchor.y.toDp()) }
            ) {
                DropdownMenuItem(
                    text = { Text("复制全文") },
                    onClick = {
                        menuExpanded = false
                        clipboard.setText(AnnotatedString(message.content))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    }
                )
                if (onQuote != null) {
                    DropdownMenuItem(
                        text = { Text("引用") },
                        onClick = {
                            menuExpanded = false
                            onQuote(message)
                        }
                    )
                }
                // 只想复制一部分时不必找菜单：长按后直接拖，就是划词选区（同笔记页的手势）。
            }
        }
    }
}

/**
 * 气泡里的「引用块」：左侧一根强调竖条 + 引用来源 + 摘要（最多两行）。
 *
 * 摘要是**落库原文**，不做任何截断存储；这里只裁显示，保证气泡不被长引用撑爆。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuotedMessageBlock(
    message: AiMessage,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .height(IntrinsicSize.Min)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = quotedSenderLabel(message),
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
            Text(
                text = quotedPreviewText(message),
                style = ZhiLuType.bodySmall,
                color = textColor.copy(alpha = 0.75f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** 引用块里显示「这是谁说的」。 */
internal fun quotedSenderLabel(message: AiMessage): String = when (message.role) {
    AiRole.USER -> "我"
    AiRole.ASSISTANT -> "AI"
    AiRole.TOOL -> message.toolName?.let { toolNameLabel(it) } ?: "工具"
    AiRole.SYSTEM -> "系统"
}

/** 引用块摘要：图片消息没有正文，给个占位，别渲染成一片空白。 */
internal fun quotedPreviewText(message: AiMessage): String = when {
    message.content.isNotBlank() -> message.content
    message.images.isNotEmpty() -> "【图片】"
    else -> "（空消息）"
}

@Composable
private fun TypingIndicator(color: Color) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500, delayMillis = index * 150),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .alpha(alpha)
                    .background(color, CircleShape)
            )
        }
    }
}
