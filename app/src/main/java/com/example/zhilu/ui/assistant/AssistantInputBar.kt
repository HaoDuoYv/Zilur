package com.example.zhilu.ui.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.zhilu.domain.ai.model.AiRef
import com.example.zhilu.domain.ai.model.AiRefKind
import com.example.zhilu.ui.theme.Radius
import com.example.zhilu.ui.theme.ShapeTokens
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 助手页输入区。
 *
 * 排版参照主流对话应用的「单一圆角容器」：附件预览、正文、工具行全部收进同一块面，
 * 容器用 `surface` + 发丝边与页面底色拉开层次，不再用 OutlinedTextField 那种
 * 「外框描边 + 内框又是一层」的双层结构。
 *
 * 正文用 [ZhiLuType.body]（16sp / 行高 26sp）而不是压缩过的 bodySmall，
 * 并把可见行数放宽到 6 行——多行提示词在旧样式里几行就挤在一起看不清。
 *
 * **不要在这里加 `imePadding()`**：键盘高度已在根层 `AppShell` 收口一次，
 * 全树只缩一次。这里再加一次就是同一个 inset 消费两遍，输入框会被顶到离键盘很高处，
 * 中间空出「与键盘等高」的一整块——这正是修之前的样子。
 * Activity 侧的配套是 `android:windowSoftInputMode="adjustResize"`，见 `AppShell` 的注释。
 */
@Composable
fun AssistantInputBar(
    text: String,
    attachedImages: List<String>,
    attachedFile: AttachedFile?,
    attachedRefs: List<AiRef>,
    isGenerating: Boolean,
    focusRequester: FocusRequester,
    onTextChange: (String) -> Unit,
    onOpenAttachSheet: () -> Unit,
    onRemoveImage: (Int) -> Unit,
    onRemoveFile: () -> Unit,
    onRemoveRef: (Int) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val canSend = (text.isNotBlank() || attachedImages.isNotEmpty() ||
        attachedFile != null || attachedRefs.isNotEmpty()) && !isGenerating

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Md, vertical = Spacing.Sm)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.Composer),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(horizontal = Spacing.Lg, vertical = Spacing.Md)) {
                if (attachedImages.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = Spacing.Md)
                    ) {
                        attachedImages.forEachIndexed { index, uri ->
                            ImageThumb(uri = uri, onRemove = { onRemoveImage(index) })
                        }
                    }
                }
                if (attachedRefs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = Spacing.Md),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
                    ) {
                        attachedRefs.forEachIndexed { index, ref ->
                            RefChip(ref = ref, onRemove = { onRemoveRef(index) })
                        }
                    }
                }
                attachedFile?.let { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.Md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = file.name,
                            style = ZhiLuType.meta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = Spacing.Sm)
                        )
                        IconButton(onClick = onRemoveFile, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "移除文件",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 正文：占位文案与输入框叠放，靠输入框自身的透明底透出占位。
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (text.isEmpty()) {
                        Text(
                            text = "问问 AI 助手，或发图识别文字…",
                            style = ZhiLuType.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 26.dp)
                            .focusRequester(focusRequester),
                        textStyle = ZhiLuType.body.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default
                        ),
                        maxLines = 6
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.Sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 附件入口：拍照 / 相册 / 本地文件 / 引用笔记收进底部弹层，工具行只留这一个图标。
                    IconButton(
                        onClick = onOpenAttachSheet,
                        enabled = !isGenerating,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加附件",
                            modifier = Modifier.size(22.dp),
                            tint = if (isGenerating) {
                                MaterialTheme.colorScheme.outline
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 生成中把「发送」换成「停止」：任务现在可以活在页面之外（前台服务保活），
                    // 没有一个随时可点的出口，用户就只能等它自己结束。
                    if (isGenerating) {
                        Surface(
                            onClick = onStop,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "停止生成",
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        Surface(
                            onClick = onSend,
                            enabled = canSend,
                            shape = CircleShape,
                            color = if (canSend) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (canSend) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageThumb(uri: String, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = Spacing.Sm)
            .size(64.dp)
    ) {
        SubcomposeAsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(ShapeTokens.Small))
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "移除图片",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun RefChip(ref: AiRef, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(percent = Radius.Chip),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.Md, end = Spacing.Xs, top = Spacing.Xs, bottom = Spacing.Xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = refChipLabel(ref),
                style = ZhiLuType.chip,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "移除引用",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

private fun refChipLabel(ref: AiRef): String {
    val kind = when (ref.kind) {
        AiRefKind.NOTE -> "笔记"
        AiRefKind.CARD -> "卡片"
        AiRefKind.BLOCK -> "块"
    }
    val title = ref.title.ifBlank { when (ref.kind) {
        AiRefKind.NOTE -> "笔记 ${ref.noteId}"
        AiRefKind.CARD -> "卡片 ${ref.cardId}"
        AiRefKind.BLOCK -> "块 ${ref.blockId}"
    } }
    return "$kind·$title"
}
