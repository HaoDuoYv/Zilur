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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.domain.model.BlockOrdering
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.KnowledgeCard
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.ui.component.ImageViewer
import com.example.zhilu.ui.component.LocalFindHighlight
import com.example.zhilu.ui.component.FindHighlight
import com.example.zhilu.ui.note.blocks.FormulaConversions
import com.example.zhilu.ui.note.blocks.LocalFormulaConversions
import com.example.zhilu.ui.note.blocks.LocalMarkChannel
import com.example.zhilu.ui.note.blocks.MarkChannel
import com.example.zhilu.ui.note.blocks.MarkRequest
import com.example.zhilu.ui.note.find.NoteFindBar
import com.example.zhilu.ui.note.find.findMatches
import com.example.zhilu.ui.note.knowledge.CardIndexRail
import com.example.zhilu.ui.note.knowledge.CardOutlineSheet
import com.example.zhilu.ui.note.knowledge.CardStickyBar
import com.example.zhilu.ui.note.knowledge.CardStickyThreshold
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.navigation.Destination
import com.example.zhilu.ui.navigation.LocalAppSnackbar
import com.example.zhilu.ui.navigation.navigateToAssistant
import com.example.zhilu.ui.note.blocks.PastePositionSheet
import com.example.zhilu.ui.note.knowledge.AddKnowledgeCardButton
import com.example.zhilu.ui.note.knowledge.KnowledgeCardItem
import com.example.zhilu.ui.note.toolbar.KnowledgeBottomToolbar
import com.example.zhilu.ui.settings.shouldRequestNotificationPermission
import kotlinx.coroutines.launch

@Composable
fun NoteEditScreen(
    navController: NavHostController,
    viewModel: NoteViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val snackbar = LocalAppSnackbar.current
    val listState = rememberLazyListState()
    // 仅用于 UI：当前激活（显示装饰与 ⋮）的块，不进入 ViewModel。
    var activeBlockId by remember { mutableStateOf<Long?>(null) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showReviewSheet by remember { mutableStateOf(false) }
    var showOutlineSheet by remember { mutableStateOf(false) }
    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findIndex by remember { mutableIntStateOf(0) }
    val findHits = remember(state.cards, findQuery) { findMatches(state.cards, findQuery) }
    // 命中数变化（改关键词、切笔记）时把指针收回有效范围
    LaunchedEffect(findHits.size) {
        if (findIndex >= findHits.size) findIndex = 0
    }
    var viewingImageUri by remember { mutableStateOf<String?>(null) }
    var pendingBranchImageBlockId by remember { mutableStateOf<Long?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    // 底部工具栏「标记」→ 编辑器 的指令通道（§3.8）
    val markChannel = remember { MarkChannel() }
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

    // 切换聚焦卡片时收起上一张卡片的块装饰。
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
                onReviewClick = { showReviewSheet = true },
                // 卡片数 ≥ 3 才露出目录入口；跳转要把标题头那一项算进去（+1）
                showOutline = state.cards.size >= 3,
                onOpenOutline = { showOutlineSheet = true },
                onOpenFind = { showFindBar = true }
            )
        }
    ) { padding ->
        // 命中高亮：与行内语义标记叠加时，查找高亮优先（后加的 style 覆盖 background）（§6.4）
        val findHighlight = if (showFindBar && findQuery.isNotBlank() && findHits.isNotEmpty()) {
            FindHighlight(
                query = findQuery,
                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                textColor = MaterialTheme.colorScheme.onSurface
            )
        } else {
            null
        }

        fun jumpToFindHit(index: Int) {
            val hit = findHits.getOrNull(index) ?: return
            coroutineScope.launch {
                listState.animateScrollToItem((hit.cardIndex + 1).coerceAtMost(state.cards.size))
            }
        }

        // 底部「标记」的目标块：优先当前激活块；否则退回聚焦卡片的第一个顶层文本块。
        // 回退是必要的 —— 否则用户必须先点 gutter 激活某块才能用这个入口，按钮常灰。
        val markTargetBlockId: Long? = activeBlockId
            ?: state.cards
                .firstOrNull { it.isFocused }
                ?.blocks
                ?.firstOrNull { it.parentBranchId == null && it.type == BlockType.TEXT }
                ?.id

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (showFindBar) {
                NoteFindBar(
                    query = findQuery,
                    onQueryChange = {
                        findQuery = it
                        findIndex = 0
                    },
                    currentIndex = findIndex,
                    total = findHits.size,
                    onPrev = {
                        if (findHits.isNotEmpty()) {
                            findIndex = (findIndex - 1 + findHits.size) % findHits.size
                            jumpToFindHit(findIndex)
                        }
                    },
                    onNext = {
                        if (findHits.isNotEmpty()) {
                            findIndex = (findIndex + 1) % findHits.size
                            jumpToFindHit(findIndex)
                        }
                    },
                    onClose = {
                        showFindBar = false
                        findQuery = ""
                        findIndex = 0
                    }
                )
            }

            // 边打边跳：关键词一变就滚到第一处命中（§6.4）
            LaunchedEffect(findQuery, findHits.size) {
                if (findHits.isNotEmpty()) jumpToFindHit(findIndex)
            }

            // 高亮经 CompositionLocal 下发，避免穿透六层到 RichText（§6.4）
            CompositionLocalProvider(
                LocalFindHighlight provides findHighlight,
                LocalMarkChannel provides markChannel,
                // 「行内公式 ↔ 公式块」只有屏幕层做得了（插块 / 改块类型），
                // 与 MarkChannel 同一模式下发；只读态不给这个能力（传 null）
                LocalFormulaConversions provides if (state.isEditing) {
                    FormulaConversions(
                        promote = { blockId, latexSource ->
                            viewModel.insertBlockAfter(blockId, BlockType.LATEX, latexSource)
                        },
                        demote = { blockId -> viewModel.demoteLatexBlock(blockId) }
                    )
                } else {
                    null
                }
            ) {
            // 用 Box 包住列表，好把「右缘索引轨」浮在页面右缘（§6.2）
            Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
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
                ) { cardIndex, card ->
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
                            cardIndex = cardIndex,
                            isEditing = state.isEditing,
                            canDelete = state.cards.size > 1,
                            onFocus = { viewModel.focusCard(card.id) },
                            onTitleChange = { viewModel.onCardTitleChange(card.id, it) },
                            onDelete = { viewModel.removeKnowledgeCard(card.id) },
                            onSetBlockEmphasis = viewModel::setBlockEmphasis,
                            onBlockContentChange = viewModel::onBlockContentChange,
                            onBlockLanguageClick = { },
                            onRemoveBlock = viewModel::removeBlock,
                            onMoveBlockUp = { blockId ->
                                BlockOrdering.arrowUpSwap(card.blocks, blockId)
                                    ?.let { (from, to) -> viewModel.moveBlock(from, to) }
                            },
                            onMoveBlockDown = { blockId ->
                                BlockOrdering.arrowDownSwap(card.blocks, blockId)
                                    ?.let { (from, to) -> viewModel.moveBlock(from, to) }
                            },
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
                            onToggleCollapsed = { viewModel.toggleCardExpanded(card.id) },
                            onToggleAllCollapsed = viewModel::toggleAllCardsExpanded,
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

                // 当前小节吸附条：卡片头滚出视口后浮出（§6.3）
                CardStickyBar(
                    cards = state.cards,
                    currentIndex = (listState.firstVisibleItemIndex - 1)
                        .coerceIn(0, (state.cards.size - 1).coerceAtLeast(0)),
                    visible = state.cards.isNotEmpty() &&
                        listState.firstVisibleItemIndex >= 1 &&
                        (
                            listState.firstVisibleItemIndex > 1 ||
                                listState.firstVisibleItemScrollOffset >
                                with(density) { CardStickyThreshold.toPx() }
                            ),
                    onJump = { index ->
                        coroutineScope.launch {
                            listState.animateScrollToItem((index + 1).coerceAtMost(state.cards.size))
                        }
                    },
                    modifier = Modifier.align(Alignment.TopStart)
                )

                // 右缘索引轨：一个刻度 = 一张卡片，拖动连续跳转（§6.2）
                CardIndexRail(
                    cards = state.cards,
                    currentIndex = (listState.firstVisibleItemIndex - 1)
                        .coerceIn(0, (state.cards.size - 1).coerceAtLeast(0)),
                    scrollActive = listState.isScrollInProgress,
                    onJump = { index ->
                        coroutineScope.launch {
                            listState.scrollToItem((index + 1).coerceAtMost(state.cards.size))
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                )
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
                    onAddBranch = { viewModel.addBlockToActiveCard(BlockType.BRANCH) },
                    markEnabled = markTargetBlockId != null,
                    onPickMarkTone = { tone ->
                        markTargetBlockId?.let { markChannel.request = MarkRequest(it, tone) }
                    },
                    onClearMark = {
                        markTargetBlockId?.let { markChannel.request = MarkRequest(it, null) }
                    }
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

    if (showOutlineSheet) {
        CardOutlineSheet(
            cards = state.cards,
            // 列表第 0 项是标题头，卡片从第 1 项开始；滚动定位时同理 +1
            currentIndex = (listState.firstVisibleItemIndex - 1)
                .coerceIn(0, (state.cards.size - 1).coerceAtLeast(0)),
            onDismiss = { showOutlineSheet = false },
            onPick = { index ->
                showOutlineSheet = false
                coroutineScope.launch {
                    listState.animateScrollToItem((index + 1).coerceAtMost(state.cards.size))
                }
            }
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

