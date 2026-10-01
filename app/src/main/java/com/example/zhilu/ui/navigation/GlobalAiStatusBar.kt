package com.example.zhilu.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.ai.model.AiTask
import com.example.zhilu.ui.assistant.toolNameLabel
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 全局 AI 任务状态条：挂在根层 [AppShell]，跨页面可见。
 * 有活跃生成任务时，顶部浮出胶囊提示「AI 生成中 · 工具名」，点击跳转助手页。
 */
@Composable
fun GlobalAiStatusBar(
    activeTasks: List<AiTask>,
    onOpenAssistant: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (activeTasks.isEmpty()) return

    val currentTool = activeTasks.lastOrNull { it.toolName != null }?.toolName
    val count = activeTasks.size

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .statusBarsPadding(),
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onOpenAssistant)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.8.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = buildString {
                    append("AI 生成中")
                    if (count > 1) append("（$count）")
                    currentTool?.let { append(" · ${toolNameLabel(it)}") }
                },
                style = ZhiLuType.chip,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = "查看",
                style = ZhiLuType.meta,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
            )
        }
    }
}
