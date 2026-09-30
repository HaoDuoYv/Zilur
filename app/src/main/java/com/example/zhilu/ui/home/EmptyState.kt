package com.example.zhilu.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.zhilu.ui.component.AppEmptyState

@Composable
fun EmptyState(onGetStarted: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AppEmptyState(
            onAction = onGetStarted,
            icon = "知",
            title = "从这里开始记录",
            description = "写下第一条知识，之后可按标签、内容和时间找回它。",
            buttonText = "开始记录"
        )
    }
}
