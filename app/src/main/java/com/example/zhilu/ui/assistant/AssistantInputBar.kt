package com.example.zhilu.ui.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.shadow
import com.example.zhilu.ui.component.AppCircleButton
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.palettePaint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.zhilu.domain.ai.model.AiRef
import com.example.zhilu.domain.ai.model.AiRefKind
import com.example.zhilu.domain.model.AiMessage
import com.example.zhilu.ui.theme.LocalReducedMotion
import com.example.zhilu.ui.theme.MotionDuration
import com.example.zhilu.ui.theme.MotionEasing
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
 *
 * @param attachPanelOpen 附件面板当前是否展开。加号是**切换**而不是「打开」，
 *   展开时图标转 45° 变成「收起」的意思，用户得看得出再点一次会回去。
 * @param onToggleAttachPanel 点加号。展开 / 收起由调用方统一裁决，这里只上报点击。
 * @param onComposerFocused 输入框拿到焦点。面板开着时点输入框意味着「我要打字了」，
 *   调用方据此收起面板把键盘让回来——不这么做的话键盘会被面板挡住（面板与键盘同高度）。
 */
@Composable
fun AssistantInputBar(
    text: String,
    attachedImages: List<String>,
    attachedFile: AttachedFile?,
    attachedRefs: List<AiRef>,
    quotedMessage: AiMessage?,
    isGenerating: Boolean,
    focusRequester: FocusRequester,
    attachPanelOpen: Boolean,
    onTextChange: (String) -> Unit,
    onToggleAttachPanel: () -> Unit,
    onComposerFocused: () -> Unit,
    onRemoveImage: (Int) -> Unit,
    onRemoveFile: () -> Unit,
    onRemoveRef: (Int) -> Unit,
    onClearQuoted: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val canSend = (text.isNotBlank() || attachedImages.isNotEmpty() ||
        attachedFile != null || attachedRefs.isNotEmpty()) && !isGenerating
    val paint = palettePaint(LocalThemePalette.current)
    val composerBorder = paint.componentBorder

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Md, vertical = Spacing.Sm)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (composerBorder == Color.Unspecified) {
                        Modifier
                    } else {
                        // 动森：输入区也描边（比卡片粗一档 —— 它是整屏最大的一块面）
                        Modifier.shadow(
                            elevation = 5.dp,
                            shape = RoundedCornerShape(Radius.Composer),
                            ambientColor = paint.cardShadow,
                            spotColor = paint.cardShadow
                        )
                    }
                ),
            shape = RoundedCornerShape(Radius.Composer),
            color = MaterialTheme.colorScheme.surface,
            border = if (composerBorder == Color.Unspecified) {
                // 纸墨：只有一根发丝线，靠底色分层
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            } else {
                BorderStroke(2.5.dp, composerBorder)
            }
        ) {
            Column(modifier = Modifier.padding(horizontal = Spacing.Lg, vertical = Spacing.Md)) {
                // 引用条：待发送的「引用回复」——发送前随时可取消（右侧 ✕）。
                quotedMessage?.let { quoted ->
                    QuotedMessageBar(
                        message = quoted,
                        onClear = onClearQuoted,
                        modifier = Modifier.padding(bottom = Spacing.Md)
                    )
                }
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
                            .focusRequester(focusRequester)
                            // 面板开着时点输入框 = 要打字：通知调用方把面板收掉。
                            // 放在这里而不是给输入框包一层 clickable，是因为后者会把手势吃掉。
                            .onFocusChanged { if (it.isFocused) onComposerFocused() },
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
                    // 附件入口：拍照 / 相册 / 本地文件 / 引用笔记收进附件面板，工具行只留这一个图标。
                    // 它是「切换」而不是「打开」：再点一次收起面板并把键盘还回来。
                    val addRotation by animateFloatAsState(
                        targetValue = if (attachPanelOpen) 45f else 0f,
                        animationSpec = if (LocalReducedMotion.current) {
                            snap()
                        } else {
                            tween(MotionDuration.Short, easing = MotionEasing.Standard)
                        },
                        label = "attach_add_rotation"
                    )
                    IconButton(
                        onClick = onToggleAttachPanel,
                        enabled = !isGenerating,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = if (attachPanelOpen) "收起附件面板" else "添加附件",
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer { rotationZ = addRotation },
                            tint = if (isGenerating) {
                                MaterialTheme.colorScheme.outline
                            } else if (attachPanelOpen) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 生成中把「发送」换成「停止」：任务现在可以活在页面之外（前台服务保活），
                    // 没有一个随时可点的出口，用户就只能等它自己结束。
                    // 动森下这两颗走 AppCircleButton —— 带厚度的实体圆钮，按下会沉下去。
                    if (isGenerating) {
                        AppCircleButton(
                            icon = Icons.Default.Stop,
                            contentDescription = "停止生成",
                            onClick = onStop,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        AppCircleButton(
                            icon = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            onClick = onSend,
                            enabled = canSend,
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
                        )
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

/**
 * 引用条目 chip。输入条用它（带移除按钮），消息气泡用它展示「当时引用了什么」（只读，[onRemove] 传 null）。
 */
@Composable
internal fun RefChip(
    ref: AiRef,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = Radius.Chip),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(
                start = Spacing.Md,
                end = if (onRemove != null) Spacing.Xs else Spacing.Md,
                top = Spacing.Xs,
                bottom = Spacing.Xs
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = refChipLabel(ref),
                style = ZhiLuType.chip,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (onRemove != null) {
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
}

/**
 * 输入区顶部的「引用消息」条：告诉用户这条消息会引用谁，✕ 可取消。
 *
 * 形态与气泡里的引用块一致（左侧强调竖条 + 来源 + 一行摘要），
 * 摘要在输入区只给一行 —— 这里越矮，输入框越不容易被挤到键盘外。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuotedMessageBar(
    message: AiMessage,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ShapeTokens.Small))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .height(IntrinsicSize.Min)
            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            Text(
                text = "引用 ${quotedSenderLabel(message)}",
                style = ZhiLuType.chip,
                color = accent
            )
            Text(
                text = quotedPreviewText(message),
                style = ZhiLuType.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "取消引用",
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
