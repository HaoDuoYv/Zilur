package com.example.zhilu.ui.note

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.component.ImageViewer
import com.example.zhilu.ui.component.TagChip
import com.example.zhilu.ui.note.blocks.EditableBlock
import com.example.zhilu.ui.note.blocks.ReadOnlyBlock
import com.example.zhilu.ui.note.tag.TagPickerInline
import com.example.zhilu.ui.note.toolbar.BlockToolbar
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
    var viewingImageUri by remember { mutableStateOf<String?>(null) }
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

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            uri?.let { viewModel.addImageFromGallery(it) }
        }
    )

    val imagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("需要存储权限才能选择图片")
                }
            }
        }
    )

    fun requestNotificationPermissionIfNeeded() {
        if (context.shouldRequestNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun launchGalleryPicker() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            imagePermissionLauncher.launch(permission)
        }
    }

    LaunchedEffect(noteId) {
        if (noteId > 0L) viewModel.load(noteId)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, navController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val handle = navController.currentBackStackEntry?.savedStateHandle
                val uri = handle?.get<String?>("capturedImageUri")
                if (uri != null) {
                    viewModel.addImageBlock(uri)
                    handle["capturedImageUri"] = null
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                    SaveStatusIndicator(status = state.saveStatus)
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
            val bottomPadding = if (state.isEditing) 0.dp else 32.dp
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding)
            ) {
                if (state.isEditing) {
                    item {
                        TitleInput(
                            title = state.title,
                            onTitleChange = viewModel::onTitleChange,
                            modifier = Modifier.fillMaxWidth()
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
                    itemsIndexed(state.blocks, key = { _, block -> block.id }) { index, block ->
                        val prevType = state.blocks.getOrNull(index - 1)?.type
                        val topPadding = when {
                            block.type == BlockType.DIVIDER || prevType == BlockType.DIVIDER -> 24.dp
                            prevType != null && prevType == block.type -> 8.dp
                            prevType != null -> 16.dp
                            else -> 0.dp
                        }
                        val showTopDivider = prevType != null &&
                            prevType == block.type &&
                            block.type != BlockType.DIVIDER &&
                            block.type != BlockType.IMAGE &&
                            block.type != BlockType.LATEX
                        EditableBlock(
                            index = index,
                            block = block,
                            total = state.blocks.size,
                            onValueChange = { viewModel.onBlockContentChange(index, it) },
                            onLanguageClick = { },
                            onRemove = { viewModel.removeBlock(index) },
                            onMoveUp = { viewModel.moveBlock(index, index - 1) },
                            onMoveDown = { viewModel.moveBlock(index, index + 1) },
                            onImageClick = if (block.type == BlockType.IMAGE) {
                                { viewingImageUri = ImageBlockContent.displayUri(block.content) }
                            } else {
                                null
                            },
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
                            showTopDivider = showTopDivider,
                            modifier = Modifier
                                .padding(top = topPadding)
                                .animateItem(),
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
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                TextButton(onClick = { viewModel.addBlock(BlockType.TEXT) }) {
                                    Text(
                                        text = "开始记录...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (state.title.isBlank()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onBackground
                                }
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
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                    itemsIndexed(state.blocks) { index, block ->
                        val prevType = state.blocks.getOrNull(index - 1)?.type
                        val topPadding = when {
                            block.type == BlockType.DIVIDER || prevType == BlockType.DIVIDER -> 24.dp
                            prevType != null && prevType == block.type -> 8.dp
                            prevType != null -> 16.dp
                            else -> 0.dp
                        }
                        val showTopDivider = prevType != null &&
                            prevType == block.type &&
                            block.type != BlockType.DIVIDER &&
                            block.type != BlockType.IMAGE &&
                            block.type != BlockType.LATEX
                        ReadOnlyBlock(
                            block = block,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(block.content))
                            },
                            showTopDivider = showTopDivider,
                            modifier = Modifier.padding(top = topPadding),
                            todoItems = state.todoItems,
                            showCompletedTodos = state.showCompletedTodos,
                            onToggleCompletedTodos = { viewModel.toggleCompletedTodos() },
                            onImageClick = if (block.type == BlockType.IMAGE) {
                                { viewingImageUri = ImageBlockContent.displayUri(block.content) }
                            } else {
                                null
                            }
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to MaterialTheme.colorScheme.background
                            )
                        )
                )
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BlockToolbar(
                            onAddText = { viewModel.addBlock(BlockType.TEXT) },
                            onAddCode = { viewModel.addBlock(BlockType.CODE) },
                            onAddImage = { navController.navigate("camera") },
                            onPickImageFromGallery = ::launchGalleryPicker,
                            onAddLink = { viewModel.addBlock(BlockType.LINK) },
                            onAddLatex = { viewModel.addBlock(BlockType.LATEX) },
                            onAddDivider = { viewModel.addBlock(BlockType.DIVIDER) }
                        )
                        if (state.isProcessingImage) {
                            Spacer(Modifier.width(12.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    viewingImageUri?.let { uri ->
        ImageViewer(
            imageUri = uri,
            onClose = { viewingImageUri = null }
        )
    }
}

@Composable
private fun SaveStatusIndicator(
    status: SaveStatus,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = status,
        modifier = modifier,
        transitionSpec = {
            fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
        },
        label = "SaveStatus"
    ) { target ->
        when (target) {
            SaveStatus.SAVING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            SaveStatus.SAVED -> {
                val scale = remember { Animatable(0.5f) }
                LaunchedEffect(Unit) {
                    scale.animateTo(1f, animationSpec = tween(durationMillis = 200))
                }
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已保存",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp * scale.value)
                )
            }
            SaveStatus.ERROR -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "保存失败",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
            }
            SaveStatus.IDLE -> {}
        }
    }
}

@Composable
private fun TitleInput(
    title: String,
    onTitleChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val textStyle = MaterialTheme.typography.headlineSmall.copy(
        color = MaterialTheme.colorScheme.onBackground
    )

    BasicTextField(
        value = title,
        onValueChange = onTitleChange,
        modifier = modifier,
        singleLine = true,
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = {}),
        decorationBox = { innerTextField ->
            if (title.isEmpty()) {
                Text(
                    text = "标题",
                    style = textStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            innerTextField()
        }
    )
}

private fun Context.shouldRequestNotificationPermission(): Boolean =
    NotificationPermissionState.requiresRuntimePermission(Build.VERSION.SDK_INT) &&
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
