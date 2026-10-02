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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.example.zhilu.ai.toolNameLabel
import com.example.zhilu.domain.ai.model.AiRef
import com.example.zhilu.domain.model.AiMessage
import com.example.zhilu.domain.model.AiRole
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.ui.navigation.AppTabScaffold
import com.example.zhilu.ui.navigation.Destination
import com.example.zhilu.ui.navigation.LocalAppSnackbar
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType
import kotlinx.coroutines.delay
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

    // 附件弹层：加号入口不再用下拉菜单，改成底部弹层（一排功能 + 最近图片）。
    // sheetState 由这里持有，关闭时先 hide 走完动画再移出组合。
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var attachSheetVisible by remember { mutableStateOf(false) }

    // 弹层打开会让主窗口失焦、键盘收起，但输入框的 Compose 焦点未必被清掉；
    // 直接 requestFocus 会因「已经聚焦」而失效。先 clearFocus 强制走一遍
    // unfocused→focused，输入框才会重新拉起 IME。中间隔一小段时间让清焦点落地。
    fun restoreComposerFocus() {
        focusManager.clearFocus()
        scope.launch {
            delay(60)
            focusRequester.requestFocus()
        }
    }

    // 无跳转的关闭（点遮罩 / 下滑）：动画收起后要回焦点、唤回键盘。
    fun dismissAttachSheet() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            attachSheetVisible = false
            restoreComposerFocus()
        }
    }

    // 拍照结果回传：相机页把 mediaId|uri 写回 savedStateHandle，助手页在 ON_RESUME 消费。
    // 与 NoteEditScreen 共用同一 key；这里剥掉 mediaId 前缀，走图片附件导入（复制进 images/）。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, navController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val handle = navController.currentBackStackEntry?.savedStateHandle
                val uri = handle?.get<String?>("capturedImageUri")
                if (uri != null) {
                    viewModel.attachImages(listOf(ImageBlockContent.displayUri(uri)))
                    handle["capturedImageUri"] = null
                    restoreComposerFocus()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 相册最近图片：有权限才查，避免 scoped storage 下拿不到还白跑一次 IO。
    var hasGalleryAccess by remember { mutableStateOf(context.canReadGallery()) }
    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasGalleryAccess = granted }
    val recentImages = rememberRecentGalleryImages(
        enabled = attachSheetVisible && hasGalleryAccess,
        limit = 8
    )

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
                    focusRequester = focusRequester,
                    onTextChange = viewModel::updateInput,
                    onOpenAttachSheet = { attachSheetVisible = true },
                    onRemoveImage = viewModel::removeImage,
                    onRemoveFile = viewModel::removeFile,
                    onRemoveRef = viewModel::removeRef,
                    onSend = viewModel::sendMessage,
                    onStop = viewModel::stopGeneration
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

    if (attachSheetVisible) {
        AttachSheet(
            sheetState = sheetState,
            canReadGallery = hasGalleryAccess,
            recentImages = recentImages,
            onRequestGalleryAccess = { galleryPermissionLauncher.launch(galleryReadPermission) },
            onTakePhoto = {
                attachSheetVisible = false
                navController.navigate(Destination.Camera.path)
            },
            onPickFromGallery = {
                attachSheetVisible = false
                runCatching { imagePicker.launch("image/*") }.onFailure {
                    scope.launch { snackbar.showSnackbar("未找到可用的图片选择器") }
                }
            },
            onPickFile = {
                attachSheetVisible = false
                runCatching {
                    filePicker.launch(
                        arrayOf("text/plain", "text/markdown", "application/octet-stream")
                    )
                }.onFailure {
                    scope.launch { snackbar.showSnackbar("未找到可用的文件选择器") }
                }
            },
            onPickRef = {
                attachSheetVisible = false
                viewModel.openRefPicker()
            },
            onPickRecentImage = { uri ->
                attachSheetVisible = false
                viewModel.attachImages(listOf(uri.toString()))
                restoreComposerFocus()
            },
            onDismissRequest = ::dismissAttachSheet
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

private fun queryDisplayName(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        }
    }.getOrNull()
