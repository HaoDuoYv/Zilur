package com.example.zhilu.ui.assistant

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.zhilu.ui.component.PushDrawer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
import com.example.zhilu.ui.navigation.LocalSuppressBottomBar
import com.example.zhilu.ui.theme.LocalReducedMotion
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

    // 一次性提示（如"已经在最新对话中"）：先消费再弹 —— 但不能让 `showSnackbar`（会挂起到
    // 提示消失）留在这个 effect 里：`consumeNotice()` 会把 key 变成 null，Compose 随即
    // 取消并重启本 effect，挂起中的 `showSnackbar` 会被一起取消，提示就永远不显示。
    // 所以这里只负责派发，真正的弹出交给屏幕作用域 [scope]。
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        viewModel.consumeNotice()
        scope.launch { snackbar.showSnackbar(notice) }
    }

    // ── 键盘 / 附件面板：两者共用同一块「底部高度」 ──────────────────────────
    //
    // 契约：面板高度恒等于键盘高度，且面板顶部与键盘顶部重合。
    // 于是「键盘 → 面板」只是把同一块高度里的东西换掉，输入栏一像素都不动。
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val view = LocalView.current
    val reducedMotion = LocalReducedMotion.current
    var attachPanelVisible by remember { mutableStateOf(false) }

    /**
     * 面板是否仍占着底部那块高度——**不能**直接用 [attachPanelVisible]。
     *
     * 收起面板时键盘是**从 0 长起来**的：如果同一帧就把占位撤掉，输入栏会先掉下去、
     * 再被升起的键盘顶回来，肉眼就是跳一下。所以收起之后面板要继续「握着」这块高度，
     * 直到键盘长到同样高再放手；期间 `面板高 - 键盘高` 会自己缩到 0，两者始终互补。
     */
    var panelEngaged by remember { mutableStateOf(false) }
    LaunchedEffect(attachPanelVisible) {
        if (attachPanelVisible) {
            panelEngaged = true
        } else {
            // 交棒窗口：键盘升起动画一般 250ms 内走完，给足一点余量。
            // 真没升起来（例如输入框拿不到焦点）也只是多留一会儿，到点照样放手。
            delay(PanelHandOffTimeout)
            panelEngaged = false
        }
    }

    // 键盘当前高度（px）。WindowInsets.ime 在 API 30+ 是**随键盘动画逐帧更新**的，
    // 因此下面那块底部占位会跟着键盘一起长、一起收，天然与键盘动画同步。
    val imeBottom = WindowInsets.ime.getBottom(density)
    // 记住键盘高度：面板要在键盘不在场时替它顶上同样一块高度。
    // 用 rememberSaveable 只是为了让旋转后不必重新量一次，掉了也不影响正确性。
    var lastImeHeight by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(imeBottom) {
        if (imeBottom > lastImeHeight) lastImeHeight = imeBottom
    }
    val panelHeightPx = lastImeHeight.takeIf { it > 0 }
        ?: with(density) { FallbackPanelHeight.roundToPx() }

    /**
     * 输入栏下方那块占位的高度。
     *
     * `imePadding()` 已经在根层 `AppShell` 让整棵树缩掉了 [imeBottom]，
     * 所以这里只需要补上「键盘还欠着的那一截」：`面板高 - 当前键盘高`。
     * 两者相加恒等于面板高度，键盘收起的过程中正好此消彼长——
     * 这就是「输入栏不跳」的全部秘密，不需要另外再算一次动画时长。
     */
    val bottomSpacePx = (panelHeightPx - imeBottom).coerceAtLeast(0)
    val bottomSpaceDp = with(density) { bottomSpacePx.toDp() }
    // 只有在「从没量到过键盘高度」的冷启动时才自己补动画：
    // 那种情况下没有键盘动画可蹭，不补的话占位会从 0 直接跳到目标高度。
    // 量到过键盘高度时一律 snap，交给 IME 动画逐帧驱动，避免两段动画互相拉扯。
    val bottomSpace by animateDpAsState(
        targetValue = if (panelEngaged) bottomSpaceDp else 0.dp,
        animationSpec = if (reducedMotion || lastImeHeight > 0) snap() else tween(PanelRiseDuration),
        label = "attach_panel_space"
    )

    // 面板展开时把键盘让出来——面板与键盘等高，两个都显示只会互相挤。
    fun hideKeyboard() {
        focusManager.clearFocus()
        runCatching {
            WindowCompat.getInsetsController((view.context as Activity).window, view)
                .hide(WindowInsetsCompat.Type.ime())
        }
    }

    // 弹层 / 面板收起后唤回键盘：直接 requestFocus 会因「已经聚焦」而失效
    // （关闭面板只是让窗口失焦，Compose 的焦点未必被清掉），
    // 必须先 clearFocus 强制走一遍 unfocused→focused。
    fun restoreComposerFocus() {
        focusManager.clearFocus()
        scope.launch {
            delay(60)
            focusRequester.requestFocus()
        }
    }

    /** 加号：面板展开 / 收起的唯一裁决点。 */
    fun toggleAttachPanel() {
        if (attachPanelVisible) {
            attachPanelVisible = false
            restoreComposerFocus()
        } else {
            attachPanelVisible = true
            hideKeyboard()
        }
    }

    // 历史对话抽屉的开合状态。声明在这里是为了能和"压住底栏"一起判断。
    var historyOpen by remember { mutableStateOf(false) }

    // 面板展开 / 抽屉打开期间压住底部导航：AppShell 只在「键盘可见」时才藏底栏，
    // 而这两种情况下键盘都是收着的，底栏会冒出来 —— 面板展开时它把内容区顶矮一截（输入栏跳动），
    // 抽屉打开时它会从抽屉面板下面露出一条（参考设计里抽屉是整屏盖住的）。
    // 这里跟 [panelEngaged] 而不是 [attachPanelVisible]：交棒的那 320ms 里键盘还没起来，
    // 底栏同样会冒头，必须一起压住。
    val setBottomBarSuppressed = LocalSuppressBottomBar.current
    DisposableEffect(panelEngaged, historyOpen, setBottomBarSuppressed) {
        setBottomBarSuppressed(panelEngaged || historyOpen)
        onDispose { setBottomBarSuppressed(false) }
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
        enabled = attachPanelVisible && hasGalleryAccess,
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
    // 用 isStreamingVisible（本会话）而不是 isGenerating（全局）：气泡只在本会话渲染，
    // 锚点下标必须与列表项数一致，否则会滚到一个不存在的 index（列表没布局时直接抛）。
    val bottomAnchorIndex = state.messages.size + if (state.isStreamingVisible) 1 else 0
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
    LaunchedEffect(state.messages.size, state.isStreamingVisible) {
        if (bottomAnchorIndex > 0) listState.animateScrollToItem(bottomAnchorIndex)
    }
    // 流式输出期间持续跟随，保证最新内容始终可见（用户上翻历史时不打扰）
    LaunchedEffect(state.streamingText) {
        if (!state.streamingText.isNullOrEmpty() && autoFollow) {
            listState.scrollToItem(bottomAnchorIndex)
        }
    }

    // 消息 id → 消息：渲染「引用块」时按 quotedMessageId 反查被引用的那条。
    // 一次会话消息数有限，整表建索引比每条气泡各自 firstOrNull 扫描更划算。
    val messagesById = remember(state.messages) { state.messages.associateBy { it.id } }

    /**
     * 底部状态条文案。
     *
     * 三档：本会话正在调工具 / 本会话正在生成 / **别的会话在生成**。
     * 最后一档是全局互斥的说明：输入栏此时显示「停止」，说明白为什么发不出去，
     * 好过让用户以为发送键坏了。
     */
    val statusLabel = state.toolStatus?.let { "正在调用 ${toolNameLabel(it)}…" }
        ?: when {
            state.isStreamingVisible -> "AI 正在生成…"
            state.isGenerating -> "其他对话正在生成…"
            else -> null
        }

    // 历史对话抽屉：左侧推入、原界面被推开并压暗，点右侧灰区返回（见 PushDrawer）。
    PushDrawer(
        open = historyOpen,
        onOpen = { historyOpen = true },
        onClose = { historyOpen = false },
        drawer = {
            ConversationHistoryDrawer(
                conversations = state.conversations,
                currentConversationId = state.currentConversationId,
                onNewConversation = {
                    viewModel.newConversation()
                    historyOpen = false
                },
                onSelectConversation = { id ->
                    viewModel.selectConversation(id)
                    historyOpen = false
                },
                onDeleteConversation = viewModel::deleteConversation
            )
        }
    ) {
    AppTabScaffold(
        topBar = {
            AssistantTopBar(
                onOpenHistory = { historyOpen = true },
                onNewConversation = viewModel::newConversation
            )
        },
        bottomBar = {
            Column {
                ToolStatusBar(label = statusLabel)
                AssistantInputBar(
                    text = state.inputText,
                    attachedImages = state.attachedImages,
                    attachedFile = state.attachedFile,
                    attachedRefs = state.attachedRefs,
                    quotedMessage = state.quotedMessage,
                    isGenerating = state.isGenerating,
                    focusRequester = focusRequester,
                    attachPanelOpen = attachPanelVisible,
                    onTextChange = viewModel::updateInput,
                    onToggleAttachPanel = ::toggleAttachPanel,
                    // 面板开着时点输入框 = 要打字：收起面板，键盘自然回来。
                    onComposerFocused = {
                        if (attachPanelVisible) attachPanelVisible = false
                    },
                    onRemoveImage = viewModel::removeImage,
                    onRemoveFile = viewModel::removeFile,
                    onRemoveRef = viewModel::removeRef,
                    onClearQuoted = viewModel::clearQuotedMessage,
                    onSend = viewModel::sendMessage,
                    onStop = viewModel::stopGeneration
                )

                // 键盘位：高度恒等于键盘高度的一块容器，面板就在这里面升起。
                // 它**跟着输入栏一起**被 imePadding 抬着，因此键盘收、面板起，
                // 两者对输入栏的净位移为零。
                AttachPanelSlot(
                    visible = attachPanelVisible,
                    height = bottomSpace,
                    reducedMotion = reducedMotion,
                    canReadGallery = hasGalleryAccess,
                    recentImages = recentImages,
                    onRequestGalleryAccess = {
                        galleryPermissionLauncher.launch(galleryReadPermission)
                    },
                    onTakePhoto = {
                        attachPanelVisible = false
                        navController.navigate(Destination.Camera.path)
                    },
                    onPickFromGallery = {
                        attachPanelVisible = false
                        runCatching { imagePicker.launch("image/*") }.onFailure {
                            scope.launch { snackbar.showSnackbar("未找到可用的图片选择器") }
                        }
                    },
                    onPickFile = {
                        attachPanelVisible = false
                        runCatching {
                            filePicker.launch(
                                arrayOf("text/plain", "text/markdown", "application/octet-stream")
                            )
                        }.onFailure {
                            scope.launch { snackbar.showSnackbar("未找到可用的文件选择器") }
                        }
                    },
                    onPickRef = {
                        attachPanelVisible = false
                        viewModel.openRefPicker()
                    },
                    onPickRecentImage = { uri ->
                        attachPanelVisible = false
                        viewModel.attachImages(listOf(uri.toString()))
                        restoreComposerFocus()
                    }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
        // 「当前 AI」条放在顶栏正下方：模型是可切换的**状态**，不该混进页面标题，
        // 但也得一眼看见 —— 主流对话应用都是这个位置。
        AiModelBar(
            settings = state.aiSettings,
            onSwitch = viewModel::switchActiveService,
            onManage = { navController.navigate(Destination.AiConfig.path) }
        )
        if (state.messages.isEmpty() && !state.isStreamingVisible) {
            AssistantEmptyState(
                configured = state.aiSettings.hasUsable,
                // 这条模型栏之外的内容不再需要额外留白，padding 已提到外层
                modifier = Modifier,
                // AI 配置现在有独立入口，不再藏在设置的通用列表里。
                onOpenSettings = { navController.navigate(Destination.AiConfig.path) },
                onSuggestion = viewModel::updateInput
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = Spacing.Sm)
            ) {
                val lastToolId = state.messages.lastOrNull { it.role == AiRole.TOOL }?.id
                items(state.messages, key = { it.id }) { message ->
                    AiMessageBubble(
                        message = message,
                        isStreaming = false,
                        isActiveTool = message.id == lastToolId &&
                            state.isStreamingVisible &&
                            state.toolStatus != null,
                        quotedMessage = message.quotedMessageId?.let { messagesById[it] },
                        onQuote = viewModel::quoteMessage
                    )
                }
                if (state.isStreamingVisible) {
                    item(key = "streaming") {
                        AiMessageBubble(
                            message = AiMessage(
                                id = Long.MIN_VALUE,
                                conversationId = state.currentConversationId ?: 0L,
                                role = AiRole.ASSISTANT,
                                content = state.streamingText.orEmpty()
                            ),
                            isStreaming = true
                            // 合成气泡还没落库，引用它没有意义（也查不到 id）——不给引用入口。
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
    }
    // ↑ 依次闭合：LazyColumn / else / if / Column

    if (state.showRefPicker) {
        RefNotePickerSheet(
            refs = state.refPickerNotes,
            onDismiss = viewModel::dismissRefPicker,
            onSelect = viewModel::addRef
        )
    }

    }
}

/**
 * 键盘位：一块高度由 [height] 决定的容器，面板在其中滑入 / 滑出。
 *
 * 单独抽成一个无作用域的组合函数是有原因的：直接写在 `Column` 里时，
 * `AnimatedVisibility` 会解析到 `ColumnScope` 的那个重载（content 不带
 * `AnimatedVisibilityScope`），随后内容里的可组合调用就报「只能在 @Composable 上下文」。
 * 抽出来之后作用域干净，解析回到标准的那一个。
 */
@Composable
private fun AttachPanelSlot(
    visible: Boolean,
    height: Dp,
    reducedMotion: Boolean,
    canReadGallery: Boolean,
    recentImages: List<Uri>,
    onRequestGalleryAccess: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    onPickFile: () -> Unit,
    onPickRef: () -> Unit,
    onPickRecentImage: (Uri) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            // 面板高度恒等于键盘高度，内容超了就滚动——
            // 这里必须裁剪，否则内容会溢出去顶到输入栏。
            .clipToBounds()
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = if (reducedMotion) snap() else tween(PanelRiseDuration)
            ) + fadeIn(
                animationSpec = if (reducedMotion) snap() else tween(PanelRiseDuration)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = if (reducedMotion) snap() else tween(PanelFallDuration)
            ) + fadeOut(
                animationSpec = if (reducedMotion) snap() else tween(PanelFallDuration)
            )
        ) {
            AttachPanel(
                canReadGallery = canReadGallery,
                recentImages = recentImages,
                onRequestGalleryAccess = onRequestGalleryAccess,
                onTakePhoto = onTakePhoto,
                onPickFromGallery = onPickFromGallery,
                onPickFile = onPickFile,
                onPickRef = onPickRef,
                onPickRecentImage = onPickRecentImage
            )
        }
    }
}

/**
 * 面板升起 / 落下的时长。
 *
 * 键盘（API 30+）的进出场动画大约是 220~250ms，取同一量级才能让
 * 「键盘落、面板起」读起来像同一个动作而不是两件事。
 */
private const val PanelRiseDuration = 240
private const val PanelFallDuration = 200

/**
 * 收起面板后，还留多久等键盘把高度接过去（毫秒）。
 *
 * 这段时间里底部占位按 `面板高 - 键盘高` 自己缩到 0，输入栏不动；
 * 到点无条件放手，避免键盘因为任何原因没弹起来时占位卡住。
 */
private const val PanelHandOffTimeout = 320L

/**
 * 从没量到过键盘高度时的兜底面板高度。
 *
 * 冷启动直接点加号时系统还没给过我们键盘高度，只能先按常见键盘高度（约 260dp）顶上；
 * 一旦键盘真正弹出过一次，就以实测值为准。
 */
private val FallbackPanelHeight = 260.dp

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
private fun ToolStatusBar(label: String?) {
    if (label == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.PageGutter, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LinearProgressIndicator(modifier = Modifier.weight(1f))
        Text(
            text = label,
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
