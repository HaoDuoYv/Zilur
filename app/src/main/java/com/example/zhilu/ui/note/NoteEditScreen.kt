package com.example.zhilu.ui.note
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.zhilu.ui.note.blocks.EditableBlock
import com.example.zhilu.ui.note.blocks.ReadOnlyBlock
import com.example.zhilu.ui.note.tag.TagPickerInline
import com.example.zhilu.ui.note.toolbar.BlockToolbar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Error
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.component.TagChip
import com.example.zhilu.ui.note.theme.NoteColors
import com.example.zhilu.ui.note.SaveStatus
import com.example.zhilu.ui.settings.NotificationPermissionState
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NoteEditScreen(
    navController: NavHostController,
    noteId: Long = 0L,
    viewModel: NoteViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var isDragging by remember { mutableStateOf(false) }
    var cumulativeDragOffset by remember { mutableStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (!granted) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("通知权限未开启，可在提醒中心查看到期项目")
                }
            }
        }
    )

    fun requestNotificationPermissionIfNeeded() {
        if (context.shouldRequestNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(noteId) {
        if (noteId > 0L) viewModel.load(noteId)
    }
    LaunchedEffect(navController.currentBackStackEntry) {
        val handle = navController.currentBackStackEntry?.savedStateHandle ?: return@LaunchedEffect
        handle.getStateFlow<String?>("capturedImageUri", null).collect { uri ->
            if (uri != null) {
                viewModel.addImageBlock(uri)
                handle["capturedImageUri"] = null
            }
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowUndoSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Block deleted",
                        actionLabel = "Undo"
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoRemoveBlock(event.token)
                    } else {
                        viewModel.confirmRemoveBlock(event.token)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = when {
                    state.isEditing && state.noteId == 0L -> "新建记录"
                    state.isEditing -> "编辑记录"
                    else -> "知识详情"
                },
                onBack = { navController.popBackStack() },
                actions = {
                    when (state.saveStatus) {
                        SaveStatus.SAVING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        SaveStatus.SAVED -> {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "已保存",
                                tint = NoteColors.primaryIndigo
                            )
                        }
                        SaveStatus.ERROR -> {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = "保存失败",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        SaveStatus.IDLE -> { /* show nothing */ }
                    }
                    if (state.saveStatus != SaveStatus.IDLE) {
                        Spacer(Modifier.width(8.dp))
                    }
                    if (state.isEditing) {
                        IconButton(onClick = viewModel::saveNow) {
                            Icon(Icons.Default.Check, contentDescription = "保存")
                        }
                    } else {
                        IconButton(onClick = {
                            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, state.title.ifBlank { "知识分享" })
                                putExtra(android.content.Intent.EXTRA_TEXT, buildString {
                                    appendLine(state.title.ifBlank { "知识分享" })
                                    appendLine()
                                    state.blocks.forEach { appendLine(it.content) }
                                })
                            }
                            context.startActivity(android.content.Intent.createChooser(sendIntent, null))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "分享")
                        }
                        IconButton(onClick = viewModel::startEditing) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isSaving || state.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                if (state.isEditing) {
                    item {
                        BasicTextField(
                            value = state.title,
                            onValueChange = viewModel::onTitleChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.headlineMedium.copy(
                                color = NoteColors.primaryIndigo
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = {}),
                            decorationBox = { innerTextField: @Composable () -> Unit ->
                                if (state.title.isEmpty()) {
                                    Text(
                                        text = "输入标题...",
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            color = NoteColors.titlePlaceholder
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                    item {
                        TagPickerInline(
                            availableTags = state.availableTags,
                            selectedTags = state.selectedTags,
                            onToggle = viewModel::toggleTag,
                            onCreate = viewModel::createTag,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    itemsIndexed(state.blocks) { index, block ->
                        EditableBlock(
                            index = index,
                            block = block,
                            total = state.blocks.size,
                            onValueChange = { viewModel.onBlockContentChange(index, it) },
                            onLanguageClick = { },
                            onRemove = { viewModel.removeBlock(index) },
                            onMoveUp = { viewModel.moveBlock(index, index - 1) },
                            onMoveDown = { viewModel.moveBlock(index, index + 1) },
                            onDragStart = {
                                if (!isDragging) {
                                    isDragging = true
                                    cumulativeDragOffset = 0f
                                    viewModel.setDragging(true)
                                }
                            },
                            onDragEnd = {
                                isDragging = false
                                cumulativeDragOffset = 0f
                                viewModel.setDragging(false)
                            },
                            onDrag = { offsetY ->
                                val currentIndex = state.blocks.indexOf(block)
                                if (currentIndex >= 0) {
                                    val itemHeight = listState.layoutInfo.visibleItemsInfo
                                        .find { it.index == currentIndex }?.size?.toFloat()
                                        ?: 100f
                                    val newOffset = cumulativeDragOffset + offsetY
                                    if (kotlin.math.abs(newOffset) >= itemHeight * 0.5f) {
                                        val direction = if (newOffset > 0) 1 else -1
                                        val targetIndex = (currentIndex + direction).coerceIn(0, state.blocks.lastIndex)
                                        if (targetIndex != currentIndex) {
                                            viewModel.moveBlock(currentIndex, targetIndex)
                                        }
                                        cumulativeDragOffset = 0f
                                    } else {
                                        cumulativeDragOffset = newOffset
                                    }
                                }
                            },
                            isDragging = isDragging,
                            modifier = Modifier.animateItem(),
                            todoItems = state.todoItems,
                            showCompletedTodos = state.showCompletedTodos,
                            onCreateTodo = { content, remindAt ->
                                val created = viewModel.createTodo(content, remindAt)
                                if (created && remindAt != null) {
                                    requestNotificationPermissionIfNeeded()
                                }
                                created
                            },
                            onUpdateTodo = { viewModel.updateTodo(it) },
                            onCompleteTodo = { viewModel.completeTodo(it) },
                            onToggleCompletedTodos = { viewModel.toggleCompletedTodos() }
                        )
                    }
                    if (state.blocks.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                            ) {
                                TextButton(onClick = { viewModel.addBlock(BlockType.TEXT) }) {
                                    Text(
                                        text = "开始记录...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = NoteColors.titlePlaceholder
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = state.title.ifBlank { "新建知识" },
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.title.isBlank()) NoteColors.titlePlaceholder else MaterialTheme.colorScheme.onSurface
                            )
                            if (state.selectedTags.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    state.selectedTags.forEach { tag ->
                                        TagChip(tag = tag, onClick = {})
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                    item {
                        ReviewPanel(
                            plan = state.reviewPlan,
                            isDue = state.isReviewDue,
                            isRecording = state.isRecordingReview,
                            onStart = {
                                requestNotificationPermissionIfNeeded()
                                viewModel.startReviewPlan()
                            },
                            onDisable = viewModel::disableReviewPlan,
                            onRate = { rating -> viewModel.recordReview(rating) }
                        )
                    }
                    itemsIndexed(state.blocks) { _, block ->
                        ReadOnlyBlock(
                            block = block,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(block.content))
                            },
                            todoItems = state.todoItems,
                            showCompletedTodos = state.showCompletedTodos,
                            onToggleCompletedTodos = viewModel::toggleCompletedTodos
                        )
                    }
                    if (state.blocks.isEmpty()) {
                        item {
                            Text(
                                text = "暂无正文内容",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (state.isEditing) {
                BlockToolbar(
                    onAddText = { viewModel.addBlock(BlockType.TEXT) },
                    onAddCode = { viewModel.addBlock(BlockType.CODE) },
                    onAddImage = { navController.navigate("camera") },
                    onAddLink = { viewModel.addBlock(BlockType.LINK) },
                    onAddLatex = { viewModel.addBlock(BlockType.LATEX) },
                    onAddDivider = { viewModel.addBlock(BlockType.DIVIDER) }
                )
            }
        }
    }
}


private fun Context.shouldRequestNotificationPermission(): Boolean =
    NotificationPermissionState.requiresRuntimePermission(Build.VERSION.SDK_INT) &&
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED

