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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.component.TagChip
import com.example.zhilu.ui.note.theme.NoteColors
import com.example.zhilu.ui.note.SaveStatus
import com.example.zhilu.ui.settings.NotificationPermissionState
import kotlinx.coroutines.launch

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
                        OutlinedTextField(
                            value = state.title,
                            onValueChange = viewModel::onTitleChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("标题") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge
                        )
                    }
                    item {
                        TagSelector(
                            state = state,
                            onToggleTag = viewModel::toggleTag,
                            onCreateTag = viewModel::createTag
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
                } else {
                    item {
                        ReadOnlyHeader(state)
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
                    onAddImage = { navController.navigate("camera") },
                    onAddLink = { viewModel.addBlock(BlockType.LINK) },
                    onAddLatex = { viewModel.addBlock(BlockType.LATEX) },
                    onAddCode = { viewModel.addBlock(BlockType.CODE) },
                    onAddTodo = { viewModel.addBlock(BlockType.TODO) },
                    onAddDivider = { viewModel.addBlock(BlockType.DIVIDER) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ReadOnlyHeader(state: NoteUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = state.title.ifBlank { "未命名知识" },
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (state.selectedTags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.selectedTags.forEach { tag ->
                    TagChip(tag = tag)
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun ReadOnlyBlock(
    block: Block,
    todoItems: List<TodoItem>,
    showCompletedTodos: Boolean,
    onToggleCompletedTodos: () -> Unit
) {
    when (block.type) {
        BlockType.TEXT -> Text(
            text = block.content.ifBlank { " " },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )
        BlockType.IMAGE -> ImageBlock(value = block.content)
        BlockType.LINK -> Text(
            text = block.content,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        )
        BlockType.LATEX -> ReadOnlyLatexBlock(value = block.content)
        BlockType.CODE -> ReadOnlyCodeBlock(value = block.content)
        BlockType.DIVIDER -> HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        BlockType.TODO -> TodoBlock(
            todoItems = todoItems,
            showCompletedTodos = showCompletedTodos,
            onCreateTodo = { _, _ -> false },
            onUpdateTodo = {},
            onCompleteTodo = {},
            onToggleCompletedTodos = onToggleCompletedTodos,
            readOnly = true
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TagSelector(
    state: NoteUiState,
    onToggleTag: (com.example.zhilu.domain.model.Tag) -> Unit,
    onCreateTag: (String) -> Unit
) {
    var newTagName by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("标签", style = MaterialTheme.typography.titleSmall)
        Text(
            text = if (state.availableTags.isEmpty()) {
                "暂无已添加标签，可在下方新建一个。"
            } else {
                "选择已添加过的标签，可多选；再次点击可取消。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.availableTags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.availableTags.forEach { tag ->
                    TagChip(
                        tag = tag,
                        selected = state.selectedTags.any { it.id == tag.id },
                        onClick = { onToggleTag(tag) }
                    )
                }
            }
        }
        if (state.selectedTags.isNotEmpty()) {
            Text(
                text = "已选择：${state.selectedTags.joinToString("、") { it.name }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newTagName,
                onValueChange = { newTagName = it },
                modifier = Modifier.weight(1f),
                label = { Text("新建标签") },
                singleLine = true
            )
            Button(
                onClick = {
                    onCreateTag(newTagName)
                    newTagName = ""
                }
            ) {
                Text("添加")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BlockToolbar(
    onAddText: () -> Unit,
    onAddImage: () -> Unit,
    onAddLink: () -> Unit,
    onAddLatex: () -> Unit,
    onAddCode: () -> Unit,
    onAddTodo: () -> Unit,
    onAddDivider: () -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TextButton(onClick = onAddTodo) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("TODO")
        }
        TextButton(onClick = onAddText) { Text("文字") }
        TextButton(onClick = onAddImage) { Text("拍照") }
        TextButton(onClick = onAddLink) { Text("链接") }
        TextButton(onClick = onAddLatex) { Text("公式") }
        TextButton(onClick = onAddCode) { Text("代码") }
        TextButton(onClick = onAddDivider) { Text("分割线") }
    }
}

private fun Context.shouldRequestNotificationPermission(): Boolean =
    NotificationPermissionState.requiresRuntimePermission(Build.VERSION.SDK_INT) &&
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED

private fun BlockType.displayName(): String = when (this) {
    BlockType.TODO -> "TODO"
    BlockType.TEXT -> "文字"
    BlockType.IMAGE -> "图片"
    BlockType.LINK -> "链接"
    BlockType.LATEX -> "公式"
    BlockType.CODE -> "代码"
    BlockType.DIVIDER -> "分割线"
}
