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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.KnowledgeCard
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.ReviewRating
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.component.ImageViewer
import com.example.zhilu.ui.component.TagChip
import com.example.zhilu.ui.note.blocks.PastePositionSheet
import com.example.zhilu.ui.note.knowledge.AddKnowledgeCardButton
import com.example.zhilu.ui.note.knowledge.KnowledgeCardItem
import com.example.zhilu.ui.note.tag.TagPickerInline
import com.example.zhilu.ui.note.toolbar.KnowledgeBottomToolbar
import com.example.zhilu.ui.settings.NotificationPermissionState
import com.example.zhilu.ui.theme.LocalReducedMotion
import com.example.zhilu.ui.theme.MotionDuration
import com.example.zhilu.ui.theme.motionEnterTween
import com.example.zhilu.ui.theme.motionExitTween
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
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
    var showShareSheet by remember { mutableStateOf(false) }
    var viewingImageUri by remember { mutableStateOf<String?>(null) }
    var pendingBranchImageBlockId by remember { mutableStateOf<Long?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var previousCardCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.cards.size) {
        if (state.cards.size > previousCardCount && previousCardCount > 0) {
            val lastIndex = if (state.isEditing) state.cards.size + 1 else state.cards.size
            listState.animateScrollToItem(lastIndex)
        }
        previousCardCount = state.cards.size
    }

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
            uri?.let {
                val branchId = pendingBranchImageBlockId
                if (branchId != null) {
                    viewModel.addImageFromGallery(it, branchId)
                    pendingBranchImageBlockId = null
                } else {
                    viewModel.addImageToActiveCardFromGallery(it)
                }
            }
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
                    viewModel.addImageToActiveCardFromCamera(uri)
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
                        IconButton(onClick = { showShareSheet = true }) {
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
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    if (state.isEditing) {
                        EditModeHeader(
                            title = state.title,
                            onTitleChange = viewModel::onTitleChange,
                            availableTags = state.availableTags,
                            selectedTags = state.selectedTags,
                            onToggleTag = viewModel::toggleTag,
                            onCreateTag = viewModel::createTag,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    } else {
                        ReadOnlyHeader(
                            title = state.title,
                            selectedTags = state.selectedTags,
                            reviewPlan = state.reviewPlan,
                            isReviewDue = state.isReviewDue,
                            isRecordingReview = state.isRecordingReview,
                            onStartReviewPlan = {
                                requestNotificationPermissionIfNeeded()
                                viewModel.startReviewPlan()
                            },
                            onDisableReviewPlan = viewModel::disableReviewPlan,
                            onRecordReview = viewModel::recordReview,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                itemsIndexed(
                    items = state.cards,
                    key = { _, card -> card.id }
                ) { _, card ->
                    var showPasteDialog by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .pointerInput(state.isEditing, card.id) {
                                detectTapGestures(
                                    onLongPress = {
                                        if (state.isEditing && viewModel.hasBlockInClipboard()) {
                                            viewModel.focusCard(card.id)
                                            showPasteDialog = true
                                        }
                                    }
                                )
                            }
                    ) {
                        KnowledgeCardItem(
                            card = card,
                            isEditing = state.isEditing,
                            canDelete = state.cards.size > 1,
                            onFocus = { viewModel.focusCard(card.id) },
                            onTitleChange = { viewModel.onCardTitleChange(card.id, it) },
                            onDelete = { viewModel.removeKnowledgeCard(card.id) },
                            onBlockContentChange = viewModel::onBlockContentChange,
                            onBlockLanguageClick = { },
                            onRemoveBlock = viewModel::removeBlock,
                            onMoveBlockUp = { blockId -> moveBlockUp(card, blockId, viewModel) },
                            onMoveBlockDown = { blockId -> moveBlockDown(card, blockId, viewModel) },
                            onInsertBlockAt = { index, type -> viewModel.insertBlockAt(index, type) },
                            onCopyBlock = { blockId -> viewModel.copyBlock(blockId) },
                            onImageClick = { block ->
                                viewingImageUri = ImageBlockContent.displayUri(block.content)
                            },
                            onToggleBranchExpanded = viewModel::toggleBranchExpanded,
                            onBranchTitleChange = viewModel::onBlockContentChange,
                            onBranchChildValueChange = viewModel::onBlockContentChange,
                            onBranchChildLanguageClick = { },
                            onRemoveBranchChild = viewModel::removeBlock,
                            onAddBranchChild = viewModel::addBranchChildBlock,
                            onAddBranchChildImage = { branchId ->
                                pendingBranchImageBlockId = branchId
                                launchGalleryPicker()
                            },
                            branchExpandedStates = state.branchExpandedStates,
                            todoItems = state.todoItems,
                            showCompletedTodos = state.showCompletedTodos,
                            onCreateTodo = if (state.isEditing) {
                                { content, remindAt ->
                                    val created = viewModel.createTodo(content, remindAt)
                                    if (created && remindAt != null) {
                                        requestNotificationPermissionIfNeeded()
                                    }
                                    created
                                }
                            } else null,
                            onUpdateTodo = if (state.isEditing) viewModel::updateTodo else null,
                            onCompleteTodo = if (state.isEditing) viewModel::completeTodo else null,
                            onToggleCompletedTodos = viewModel::toggleCompletedTodos,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    if (showPasteDialog) {
                        PastePositionSheet(
                            onDismiss = { showPasteDialog = false },
                            onPasteTop = {
                                viewModel.pasteBlock(0)
                                showPasteDialog = false
                            },
                            onPasteBottom = {
                                viewModel.pasteBlock(card.blocks.size)
                                showPasteDialog = false
                            },
                            onPasteEnd = {
                                viewModel.pasteBlock(null)
                                showPasteDialog = false
                            }
                        )
                    }
                }

                if (state.isEditing) {
                    item {
                        AddKnowledgeCardButton(onClick = viewModel::addKnowledgeCard)
                    }
                } else if (state.cards.all { it.blocks.isEmpty() }) {
                    item {
                        Text(
                            text = "暂无正文内容",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }

            if (state.isEditing) {
                KnowledgeBottomToolbar(
                    activeCardId = state.activeCardId,
                    onAddText = { viewModel.addBlockToActiveCard(BlockType.TEXT) },
                    onTakePhoto = { navController.navigate("camera") },
                    onPickImageFromGallery = ::launchGalleryPicker,
                    onAddLatex = { viewModel.addBlockToActiveCard(BlockType.LATEX) },
                    onAddCode = { viewModel.addBlockToActiveCard(BlockType.CODE) },
                    onAddLink = { viewModel.addBlockToActiveCard(BlockType.LINK) },
                    onAddBranch = { viewModel.addBlockToActiveCard(BlockType.BRANCH) }
                )
                if (state.isProcessingImage) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface),
                        horizontalArrangement = Arrangement.Center
                    ) {
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

    viewingImageUri?.let { uri ->
        ImageViewer(
            imageUri = uri,
            onClose = { viewingImageUri = null }
        )
    }

    if (showShareSheet) {
        ShareFormatBottomSheet(
            onDismiss = { showShareSheet = false },
            onSelect = { format ->
                showShareSheet = false
                viewModel.shareNote(format) { file, mimeType ->
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(sendIntent, "分享"))
                }
            }
        )
    }
}

@Composable
private fun SaveStatusIndicator(
    status: SaveStatus,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    AnimatedContent(
        targetState = status,
        modifier = modifier,
        transitionSpec = {
            fadeIn(animationSpec = motionEnterTween(MotionDuration.Short, enabled = !reducedMotion)) togetherWith fadeOut(animationSpec = motionExitTween(MotionDuration.Short, enabled = !reducedMotion))
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
                    scale.animateTo(1f, animationSpec = motionEnterTween(MotionDuration.Short, enabled = !reducedMotion))
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


@Composable
private fun EditModeHeader(
    title: String,
    onTitleChange: (String) -> Unit,
    availableTags: List<Tag>,
    selectedTags: List<Tag>,
    onToggleTag: (Tag) -> Unit,
    onCreateTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        TitleInput(
            title = title,
            onTitleChange = onTitleChange,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        TagPickerInline(
            availableTags = availableTags,
            selectedTags = selectedTags,
            onToggle = onToggleTag,
            onCreate = onCreateTag,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadOnlyHeader(
    title: String,
    selectedTags: List<Tag>,
    reviewPlan: ReviewPlan?,
    isReviewDue: Boolean,
    isRecordingReview: Boolean,
    onStartReviewPlan: () -> Unit,
    onDisableReviewPlan: () -> Unit,
    onRecordReview: (ReviewRating) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title.ifBlank { "新建知识" },
            style = MaterialTheme.typography.headlineSmall,
            color = if (title.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onBackground
            }
        )
        if (selectedTags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                selectedTags.forEach { tag ->
                    TagChip(tag = tag, onClick = {})
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ReviewPanel(
            plan = reviewPlan,
            isDue = isReviewDue,
            isRecording = isRecordingReview,
            onStart = onStartReviewPlan,
            onDisable = onDisableReviewPlan,
            onRate = onRecordReview
        )
    }
}

private fun moveBlockUp(card: KnowledgeCard, blockId: Long, viewModel: NoteViewModel) {
    val topLevelBlocks = card.blocks.filter { it.parentBranchId == null }
    val index = topLevelBlocks.indexOfFirst { it.id == blockId }
    if (index <= 0) return
    val targetBlock = topLevelBlocks[index - 1]
    viewModel.moveBlock(blockId, targetBlock.id)
}

private fun moveBlockDown(card: KnowledgeCard, blockId: Long, viewModel: NoteViewModel) {
    val topLevelBlocks = card.blocks.filter { it.parentBranchId == null }
    val index = topLevelBlocks.indexOfFirst { it.id == blockId }
    if (index < 0 || index >= topLevelBlocks.lastIndex) return
    val targetBlock = topLevelBlocks[index + 1]
    viewModel.moveBlock(targetBlock.id, blockId)
}

private fun Context.shouldRequestNotificationPermission(): Boolean =
    NotificationPermissionState.requiresRuntimePermission(Build.VERSION.SDK_INT) &&
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
