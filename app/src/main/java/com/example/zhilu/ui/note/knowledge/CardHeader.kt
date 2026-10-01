package com.example.zhilu.ui.note.knowledge

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.AlphaTokens

/**
 * 知识卡片头部：图钉 + 可编辑标题（聚焦态才可改），聚焦且可删时露出删除入口。
 */
@Composable
fun CardHeader(
    title: String,
    onTitleChange: (String) -> Unit,
    readOnly: Boolean,
    showDelete: Boolean,
    canDelete: Boolean,
    onDelete: () -> Unit,
    showCite: Boolean = false,
    onCiteToAi: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val textStyle = MaterialTheme.typography.titleMedium.merge(
        TextStyle(color = MaterialTheme.colorScheme.onSurface)
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.PushPin,
            contentDescription = "知识小点",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        if (readOnly) {
            Text(
                text = title.ifBlank { "知识小点" },
                style = textStyle,
                modifier = Modifier.weight(1f)
            )
        } else {
            BasicTextField(
                value = title,
                onValueChange = onTitleChange,
                modifier = Modifier.weight(1f),
                textStyle = textStyle,
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (title.isEmpty()) {
                        Text(
                            text = "输入小点名称(如:情况一)...",
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.Hint)
                        )
                    }
                    innerTextField()
                }
            )
        }

        if (showCite) {
            IconButton(onClick = onCiteToAi) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = "引用到 AI",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (showDelete) {
            IconButton(
                onClick = onDelete,
                enabled = canDelete
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = if (canDelete) "删除知识卡片" else "至少保留一张知识卡片",
                    tint = if (canDelete) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.Disabled)
                    }
                )
            }
        }
    }
}