package com.example.zhilu.ui.assistant

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.zhilu.ui.component.AppTopBar

/**
 * AI 助手顶栏。
 *
 * 布局参照主流对话应用（DeepSeek 等）：**左侧 ☰ 开历史抽屉、右侧 ＋ 新建对话**。
 * 早先是右上角一个 🕘 图标弹下拉菜单，把"翻历史"和"开新对话"两件事挤在一个入口里，
 * 历史一多那个下拉就既难翻又不像"列表"。
 *
 * 一级页，没有返回键 —— 所以用的是 [AppTopBar] 的 `leading` 插槽而不是 `onBack`。
 */
@Composable
fun AssistantTopBar(
    onOpenHistory: () -> Unit,
    onNewConversation: () -> Unit
) {
    AppTopBar(
        title = "AI 助手",
        leading = {
            TopBarAction(
                icon = Icons.Default.Menu,
                contentDescription = "历史对话",
                onClick = onOpenHistory
            )
        },
        actions = {
            TopBarAction(
                icon = Icons.Default.AddCircleOutline,
                contentDescription = "新建对话",
                onClick = onNewConversation
            )
        }
    )
}

/**
 * 顶栏图标位。
 *
 * 用 `IconButton` 而不是自拼 `Box + clickable`：后者放在 `TopAppBar` 的槽位里
 * **点不动**（真机验证过：同样写法在底栏正常、在顶栏槽位里完全收不到点击）。
 * 水波纹则用 `LocalRippleConfiguration provides null` 关掉 —— 这是 Material3 认可的开关，
 * 比换掉整个按钮实现安全。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBarAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
