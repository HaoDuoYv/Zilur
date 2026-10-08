package com.example.zhilu.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.zhilu.ai.AiPromptHandoff
import com.example.zhilu.ai.AiTaskManager
import com.example.zhilu.ui.create.CreateSheet
import com.example.zhilu.ui.create.CreateSheetViewModel
import com.example.zhilu.ui.settings.ImportPreviewDialog
import kotlinx.coroutines.launch

/**
 * 应用级 Snackbar 单例。各页通过 `LocalAppSnackbar.current.showSnackbar(...)` 触发，
 * 由根层 [AppShell] 统一渲染，避免每个页面各自挂载 SnackbarHost。
 */
val LocalAppSnackbar = staticCompositionLocalOf<SnackbarHostState> {
    error("LocalAppSnackbar is not provided. Wrap the content with AppShell.")
}

/**
 * 让页面临时压住底部导航。
 *
 * 底栏的显示条件是「平级页 && 键盘不可见」。但有些页面会用一块**与键盘等高**的面板
 * 顶替键盘（助手页的附件面板就是）：面板展开时键盘是收着的，底栏按原条件会冒出来，
 * 把内容区顶矮一截——于是输入栏被挤得往上跳。
 *
 * 状态无法从子树往上抬（页面在 AppShell 内部），所以反向提供一个 setter 下发，
 * 由页面在自己的 DisposableEffect 里声明「此刻别显示底栏」。
 */
val LocalSuppressBottomBar = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

/**
 * 唯一的根 Scaffold：
 * - 只在底部导航平级页（[TopLevelRoutes]）显示底栏；
 * - 键盘弹出时隐藏底栏，避免遮挡输入；
 * - 系统栏 inset 归口于此（contentWindowInsets 归零，底栏自身消费导航栏 inset）；
 * - 承载全局唯一的 SnackbarHost；
 * - 顶部叠加全局 AI 任务状态条（跨页面可见）。
 */
@Composable
fun AppShell(
    navController: NavHostController,
    startDestination: String = Destination.Home.path,
    aiTaskManager: AiTaskManager,
    promptHandoff: AiPromptHandoff,
    createSheetViewModel: CreateSheetViewModel = hiltViewModel()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isTopLevel = currentDestination == null ||
        currentDestination.hierarchy.any { it.route?.substringBefore('?') in TopLevelRoutes }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var bottomBarSuppressed by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val aiState by aiTaskManager.state.collectAsState()
    val hasActiveTask = aiState.activeTasks.isNotEmpty()

    // ── 底栏中央 ＋ 的「新建 / 导入」弹层 ────────────────────────────────
    // 挂在 AppShell 而不是某个页面：它是**全局唯一**的新建入口，四个平级页都能唤起。
    var showCreateSheet by remember { mutableStateOf(false) }
    val createState by createSheetViewModel.uiState.collectAsState()
    val jsonImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            showCreateSheet = false
            uri?.let(createSheetViewModel::importJson)
        }
    )
    val dtkImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            showCreateSheet = false
            uri?.let(createSheetViewModel::parseDtk)
        }
    )
    val shellScope = rememberCoroutineScope()
    LaunchedEffect(createState.message) {
        val message = createState.message ?: return@LaunchedEffect
        // 先消费再弹（消息是一次性的，留着会反复弹）；但 showSnackbar 必须派发到屏幕作用域：
        // 它挂起到提示消失，而 consumeMessage() 会让本 effect 的 key 变化 → effect 被取消
        // → 挂起中的提示被一起取消（详见 AGENTS.md「UI 状态与一次性提示」）。
        createSheetViewModel.consumeMessage()
        shellScope.launch { snackbarHostState.showSnackbar(message) }
    }

    // 全局 AI 状态条以「占位」方式挂在最上方（而非浮层叠加）：出现时把整页内容下移，
    // 避免压住各页自带的顶栏。状态条自身已消费状态栏 inset，因此下方子树需要
    // 显式 consumeWindowInsets(statusBars)，否则各页会再让位一次导致顶部留白翻倍。
    //
    // IME 也在这里收口：键盘高度只让整棵树缩一次，各页不必各自 imePadding。
    // 这与 API 30 以下「窗口被 IME 顶掉一块」的原生行为一致，于是不必再为不同版本写分支。
    // 前提是 Activity 声明了 windowSoftInputMode="adjustResize"——否则系统会把默认的
    // adjustUnspecified 解析成 adjustPan，由框架先把窗口表面整体上推一次，
    // 这里的 padding 再推一次，输入框与键盘之间就会空出「与键盘等高」的一整块。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        if (hasActiveTask) {
            GlobalAiStatusBar(
                activeTasks = aiState.activeTasks,
                onOpenAssistant = { navController.navigateToAssistant() }
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (hasActiveTask) Modifier.consumeWindowInsets(WindowInsets.statusBars)
                    else Modifier
                )
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (isTopLevel && !imeVisible && !bottomBarSuppressed) {
                        BottomBar(
                            navController = navController,
                            onCreateClick = { showCreateSheet = true },
                            createExpanded = showCreateSheet
                        )
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { inner ->
                val bottomPadding = if (isTopLevel) inner.calculateBottomPadding() else 0.dp
                CompositionLocalProvider(
                    LocalAppSnackbar provides snackbarHostState,
                    LocalSuppressBottomBar provides { bottomBarSuppressed = it }
                ) {
                    AppNavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(bottom = bottomPadding)
                    )
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateSheet(
            onDismiss = { showCreateSheet = false },
            onBlank = {
                showCreateSheet = false
                createSheetViewModel.createBlankNote { noteId ->
                    navController.navigate(Destination.NoteEdit.createRoute(noteId))
                }
            },
            onImportJson = {
                jsonImportLauncher.launch(arrayOf("application/json", "text/*"))
            },
            onImportDtk = {
                dtkImportLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
            },
            onAiCreate = {
                showCreateSheet = false
                // 预填一句「帮我创建：」——用户接着补主题就能发，省掉"我该怎么说"的一步。
                // 话术走进程级交接而不是路由参数：带参数的路由会新建助手页 entry，
                // 把正在跑的任务状态 / 当前会话 / 输入草稿一起重置（真机 bug）。
                promptHandoff.handoff(AI_CREATE_PREFILL)
                navController.navigateToAssistant()
            }
        )
    }

    createState.dtkPreview?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            onConfirm = createSheetViewModel::confirmDtkImport,
            onDismiss = createSheetViewModel::dismissDtkPreview
        )
    }
}

/** 「AI 创建」的预填开场白。 */
private const val AI_CREATE_PREFILL = "帮我创建："
