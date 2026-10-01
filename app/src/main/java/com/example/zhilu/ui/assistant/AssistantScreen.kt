package com.example.zhilu.ui.assistant

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.ui.navigation.AppTabScaffold
import com.example.zhilu.ui.navigation.Destination
import com.example.zhilu.ui.navigation.LocalAppSnackbar
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType
import kotlinx.coroutines.launch

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
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
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
            // IME 让位由 AssistantInputBar 自身处理；Column 底部贴着 Scaffold，
            // 输入栏被键盘顶起时整组（含工具状态条）会一起上移。
            Column {
                ToolStatusBar(toolStatus = state.toolStatus)
                AssistantInputBar(
                    text = state.inputText,
                    attachedImages = state.attachedImages,
                    attachedFile = state.attachedFile,
                    isGenerating = state.isGenerating,
                    onTextChange = viewModel::updateInput,
                    onPickImages = { imagePicker.launch("image/*") },
                    onRemoveImage = viewModel::removeImage,
                    onPickFile = {
                        filePicker.launch(
                            arrayOf("text/plain", "text/markdown", "application/octet-stream")
                        )
                    },
                    onRemoveFile = viewModel::removeFile,
                    onSend = viewModel::sendMessage
                )
            }
        }
    ) { padding ->
        if (state.messages.isEmpty()) {
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
                items(state.messages, key = { it.id }) { message ->
                    AiMessageBubble(
                        message = message,
                        isStreaming = state.streamingMessageId == message.id
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolStatusBar(toolStatus: String?) {
    if (toolStatus == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.PageGutter, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LinearProgressIndicator(modifier = Modifier.weight(1f))
        Text(
            text = "正在调用 $toolStatus…",
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