package com.example.zhilu.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable

/**
 * 底部导航平级页的页面壳。
 *
 * 与普通 Scaffold 的区别：**不再承载底栏与 SnackbarHost**（由根层 [AppShell] 提供），
 * 且 contentWindowInsets 只保留顶部与横向系统栏——纵向底部由根层底栏让位，避免 inset 双计。
 * [bottomBar] 供平级页自带的底部操作区使用（如助手页输入栏），需由调用方自行处理 IME padding。
 */
@Composable
fun AppTabScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = topBar,
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        content = content
    )
}