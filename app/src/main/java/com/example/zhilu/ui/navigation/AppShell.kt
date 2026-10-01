package com.example.zhilu.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.zhilu.ai.AiTaskManager

/**
 * 应用级 Snackbar 单例。各页通过 `LocalAppSnackbar.current.showSnackbar(...)` 触发，
 * 由根层 [AppShell] 统一渲染，避免每个页面各自挂载 SnackbarHost。
 */
val LocalAppSnackbar = staticCompositionLocalOf<SnackbarHostState> {
    error("LocalAppSnackbar is not provided. Wrap the content with AppShell.")
}

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
    aiTaskManager: AiTaskManager
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isTopLevel = currentDestination == null ||
        currentDestination.hierarchy.any { it.route in TopLevelRoutes }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val snackbarHostState = remember { SnackbarHostState() }
    val aiState by aiTaskManager.state.collectAsState()
    val hasActiveTask = aiState.activeTasks.isNotEmpty()

    // 全局 AI 状态条以「占位」方式挂在最上方（而非浮层叠加）：出现时把整页内容下移，
    // 避免压住各页自带的顶栏。状态条自身已消费状态栏 inset，因此下方子树需要
    // 显式 consumeWindowInsets(statusBars)，否则各页会再让位一次导致顶部留白翻倍。
    Column(modifier = Modifier.fillMaxSize()) {
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
                    if (isTopLevel && !imeVisible) {
                        BottomBar(navController = navController)
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { inner ->
                val bottomPadding = if (isTopLevel) inner.calculateBottomPadding() else 0.dp
                CompositionLocalProvider(LocalAppSnackbar provides snackbarHostState) {
                    AppNavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(bottom = bottomPadding)
                    )
                }
            }
        }
    }
}
