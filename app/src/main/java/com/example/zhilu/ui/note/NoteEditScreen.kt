package com.example.zhilu.ui.note

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.ui.component.ImageViewer
import com.example.zhilu.ui.component.TagChip
import com.example.zhilu.ui.navigation.Destination
import com.example.zhilu.ui.navigation.LocalAppSnackbar
import com.example.zhilu.ui.navigation.navigateToAssistant
import com.example.zhilu.ui.note.blocks.PastePositionSheet
import com.example.zhilu.ui.note.knowledge.AddKnowledgeCardButton
import com.example.zhilu.ui.note.knowledge.KnowledgeCardItem
import com.example.zhilu.ui.note.tag.TagPickerInline
import com.example.zhilu.ui.note.toolbar.KnowledgeBottomToolbar
import com.example.zhilu.ui.settings.NotificationPermissionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditScreen(
    navController: NavHostController,
    viewModel: NoteViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val snackbar = LocalAppSnackbar.current
    val listState = rememberLazyListState()
    // 仅用�? UI：当前激活（显示装饰�? ⋮）的块，不进入 ViewModel�?
    var activeBlockId by remember { mutableStateOf<Long?>(null) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showReviewSheet by remember { mutableStateOf(false) }
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
                    snackbar.showSnackbar("通知权限未开启，可在提醒中心查看到期项目")
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
                    snackbar.showSnackbar("需要存储权限才能选择图片")
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

    // 切换聚焦卡片时收起上一张卡片的块装饰�?
    LaunchedEffect(state.activeCardId) {
        activeBlockId = null
    }

    // 笔记数据由 NoteViewModel.init 从 savedStateHandle 的 noteId 载入；
    // 这里不再重复调用 load()，否则每次进入都会多消耗一次卡片临时 id。
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
                // 从助手页回来时，笔记可能已被 AI 工具改写：浏览态下重新拉取，避免看到旧快照。
                viewModel.refreshIfBrowsing()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowUndoSnackbar -> {
                    val result = snackbar.showSnackbar(
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            NoteTopBar(
                title = when {
                    state.isEditing && state.noteId == 0L -> "新建记录"
                    state.isEditing -> "编辑记录"
                    else -> "知识详情"
                },
                isEditing = state.isEditing,
                saveStatus = state.saveStatus,
                showReview = !state.isEditing,
                isReviewDue = state.isReviewDue,
                onBack = { navController.popBackStack() },
                onSave = viewModel::saveNow,
                onShare = { showShareSheet = true },
                onStartEditing = viewModel::startEditing,
                onReviewClick = { showReviewSheet = true }
            )
        }
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
                            activeBlockId = activeBlockId,
                            onActivateBlock = { blockId -> activeBlockId = blockId },
                            onReorderBlock = viewModel::moveBlock,
                            onDragStateChange = viewModel::setDragging,
                            onCiteToAi = {
                                viewModel.citeCardToAi(card)
                                navController.navigateToAssistant()
                            },
                            onCiteBlockToAi = { blockId ->
                                card.blocks.firstOrNull { it.id == blockId }?.let {
                                    viewModel.citeBlockToAi(it)
                                }
                                navController.navigateToAssistant()
                            },
                            isGenerating = card.id in state.generatingCardIds,
                            generatingBlockIds = state.generatingBlockIds,
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
                    onTakePhoto = { navController.navigate(Destination.Camera.path) },
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

    if (showReviewSheet) {
        ReviewSheet(
            plan = state.reviewPlan,
            isDue = state.isReviewDue,
            isRecording = state.isRecordingReview,
            onStart = {
                requestNotificationPermissionIfNeeded()
                viewModel.startReviewPlan()
                showReviewSheet = false
            },
            onDisable = {
                viewModel.disableReviewPlan()
                showReviewSheet = false
            },
            onRate = {
                viewModel.recordReview(it)
                showReviewSheet = false
            },
            onDismiss = { showReviewSheet = false }
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
