package com.example.zhilu.ui.assistant

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.domain.ai.model.AiRef
import com.example.zhilu.domain.model.AiMessage
import com.example.zhilu.domain.model.AiRole
import com.example.zhilu.ui.navigation.AppTabScaffold
import com.example.zhilu.ui.navigation.Destination
import com.example.zhilu.ui.navigation.LocalAppSnackbar
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    navController: NavHostController,
    viewModel: AssistantViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val snackbar = LocalAppSnackbar.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            viewModel.attachImages(uris.map { it.toString() })
        }
    )

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                val name = queryDisplayName(context, it) ?: "附件"
                val content = runCatching {
                    context.contentResolver.openInputStream(it)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    }
                }.getOrNull()
                if (content == null) {
                    scope.launch { snackbar.showSnackbar("无法读取文件内容") }
                } else {
                    viewModel.attachFile(name, content)
                }
            }
        }
    )

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }
    // 末尾锚点项的下标：消息数 + 可选的流式气泡。滚到它即等于滚到列表底部，
    // 而 animateScrollToItem(最后一条) 只会把该条的「顶部」对齐视口，长回答的末尾会被推到屏幕外。
    val bottomAnchorIndex = state.messages.size + if (state.isGenerating) 1 else 0
    val atBottom by remember {
        derivedStateOf { !listState.canScrollForward }
    }
    // 自动跟随开关：用户主动上翻离开底部时暂停跟随，重新回到底部后恢复。
    var autoFollow by remember { mutableStateOf(true) }
    LaunchedEffect(atBottom, listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !atBottom) autoFollow = false
        if (atBottom) autoFollow = true
    }

    // 新消息或开始生成时，平滑滚到底部
    LaunchedEffect(state.messages.size, state.isGenerating) {
        if (bottomAnchorIndex > 0) listState.animateScrollToItem(bottomAnchorIndex)
    }
    // 流式输出期间持续跟随，保证最新内容始终可见（用户上翻历史时不打扰）
    LaunchedEffect(state.streamingText) {
        if (!state.streamingText.isNullOrEmpty() && autoFollow) {
            listState.scrollToItem(bottomAnchorIndex)
        }
    }

    AppTabScaffold(
        topBar = {
            AssistantTopBar(
                conversations = state.conversations,
                onNewConversation = viewModel::newConversation,
                onSelectConversation = viewModel::selectConversation,
                onDeleteConversation = viewModel::deleteConversation
            )
        },
        bottomBar = {
            Column {
                ToolStatusBar(toolStatus = state.toolStatus, isGenerating = state.isGenerating)
                AssistantInputBar(
                    text = state.inputText,
                    attachedImages = state.attachedImages,
                    attachedFile = state.attachedFile,
                    attachedRefs = state.attachedRefs,
                    isGenerating = state.isGenerating,
                    onTextChange = viewModel::updateInput,
                    // 部分设备/精简系统没有能处理 GET_CONTENT 的选择器，
                    // 直接 launch 会抛 ActivityNotFoundException 把应用打崩，这里降级为提示。
                    onPickImages = {
                        runCatching { imagePicker.launch("image/*") }.onFailure {
                            scope.launch { snackbar.showSnackbar("未找到可用的图片选择器") }
                        }
                    },
                    onRemoveImage = viewModel::removeImage,
                    onPickFile = {
                        runCatching {
                            filePicker.launch(
                                arrayOf("text/plain", "text/markdown", "application/octet-stream")
                            )
                        }.onFailure {
                            scope.launch { snackbar.showSnackbar("未找到可用的文件选择器") }
                        }
                    },
                    onRemoveFile = viewModel::removeFile,
                    onPickRef = viewModel::openRefPicker,
                    onRemoveRef = viewModel::removeRef,
                    onSend = viewModel::sendMessage
                )
            }
        }
    ) { padding ->
        if (state.messages.isEmpty() && !state.isGenerating) {
            AssistantEmptyState(
                configured = state.aiConfig.isConfigured,
                modifier = Modifier.padding(padding),
                onOpenSettings = { navController.navigate(Destination.Settings.path) },
                onSuggestion = viewModel::updateInput
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = Spacing.Sm)
            ) {
                val lastToolId = state.messages.lastOrNull { it.role == AiRole.TOOL }?.id
                items(state.messages, key = { it.id }) { message ->
                    AiMessageBubble(
                        message = message,
                        isStreaming = false,
                        isActiveTool = message.id == lastToolId &&
                            state.isGenerating &&
                            state.toolStatus != null
                    )
                }
                if (state.isGenerating) {
                    item(key = "streaming") {
                        AiMessageBubble(
                            message = AiMessage(
                                id = Long.MIN_VALUE,
                                conversationId = state.currentConversationId ?: 0L,
                                role = AiRole.ASSISTANT,
                                content = state.streamingText.orEmpty()
                            ),
                            isStreaming = true
                        )
                    }
                }
                // 滚动锚点：始终置于列表末尾，作为「滚到底部」的落点
                item(key = "bottom-anchor") {
                    Spacer(modifier = Modifier.height(Spacing.Sm))
                }
            }
        }
    }

    if (state.showRefPicker) {
        RefNotePickerSheet(
            refs = state.refPickerNotes,
            onDismiss = viewModel::dismissRefPicker,
            onSelect = viewModel::addRef
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefNotePickerSheet(
    refs: List<AiRef>,
    onDismiss: () -> Unit,
    onSelect: (AiRef) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.PageGutter, vertical = Spacing.Sm)
        ) {
            Text(
                text = "引用笔记",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (refs.isEmpty()) {
                Text(
                    text = "知识库还没有笔记",
                    style = ZhiLuType.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.Lg)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(refs, key = { it.noteId }) { ref ->
                        androidx.compose.material3.ListItem(
                            headlineContent = { Text(ref.title.ifBlank { "笔记 ${ref.noteId}" }) },
                            supportingContent = { Text("引用整篇笔记", style = ZhiLuType.meta) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable { onSelect(ref) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolStatusBar(toolStatus: String?, isGenerating: Boolean) {
    if (toolStatus == null && !isGenerating) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.PageGutter, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LinearProgressIndicator(modifier = Modifier.weight(1f))
        Text(
            text = if (toolStatus != null) {
                "正在调用 ${toolNameLabel(toolStatus)}…"
            } else {
                "AI 正在生成…"
            },
            style = ZhiLuType.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 工具名 → 友好中文（供状态条与徽章共用）。 */
internal fun toolNameLabel(toolName: String): String = when (toolName) {
    "list_notes" -> "读取笔记列表"
    "search_notes" -> "搜索笔记"
    "get_note" -> "读取笔记"
    "create_note" -> "创建笔记"
    "update_note" -> "修改笔记"
    "add_tags" -> "更新标签"
    else -> toolName
}

private fun queryDisplayName(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        }
    }.getOrNull()
