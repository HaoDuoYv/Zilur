package com.example.zhilu.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.Radius
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

private val quickSuggestions = listOf(
    "帮我整理一份关于二次型的知识点",
    "我的笔记里有哪些内容？",
    "搜索包含微积分的笔记"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantEmptyState(
    configured: Boolean,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit,
    onSuggestion: (String) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "AI 助手",
            style = ZhiLuType.noteTitle,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Spacing.Md))
        Text(
            text = if (configured) {
                "用自然语言创建、整理、修改和读取你的知识点，也可以发图识别文字。"
            } else {
                "尚未配置 AI 服务，请先在设置中选择供应商并填写 Key。"
            },
            style = ZhiLuType.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!configured) {
            Spacer(modifier = Modifier.height(Spacing.Lg))
            Button(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(Radius.Field)
            ) {
                Text("去设置", style = ZhiLuType.chip)
            }
        } else {
            Spacer(modifier = Modifier.height(Spacing.Xl))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                quickSuggestions.forEach { suggestion ->
                    SuggestionChip(text = suggestion, onClick = { onSuggestion(suggestion) })
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = text,
            style = ZhiLuType.chip,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}