package com.example.zhilu.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.ui.component.rememberTagAccent
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/** 重命名标签：改的是 `tags.name`，**同一 id、关联不断**，笔记里的胶囊即时同步。 */
@Composable
fun TagRenameDialog(
    tag: Tag,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(tag.id) { mutableStateOf(tag.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名标签") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 删除标签：仅移除关联，正文文本不动（标签不是从正文解析出来的）。 */
@Composable
fun TagDeleteDialog(
    tag: Tag,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除标签「${tag.name}」？") },
        text = { Text("仅移除标签关联，不会删除笔记内容。") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("删除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 合并标签：在所选标签里挑一个「保留」，其余删除、关联改挂到保留项。
 *
 * 目标限定在**所选集合**内 —— 想并到一个没选的标签上，把它一起选进来即可；
 * 保留项的名字与颜色原样保留（合并是收束，不是重命名）。
 */
@Composable
fun TagMergeDialog(
    items: List<TagFilterItem>,
    onConfirm: (targetId: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var targetId by remember(items) { mutableStateOf(items.firstOrNull()?.tag?.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("合并 ${items.size} 个标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
                Text(
                    text = "选择要保留的标签，其余标签会被删除，它们的笔记改为挂在保留的标签下。",
                    style = ZhiLuType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                items.forEach { item ->
                    val selected = item.tag.id == targetId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { targetId = item.tag.id }
                            )
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(rememberTagAccent(item.tag.color))
                        )
                        Text(
                            text = item.tag.name,
                            style = ZhiLuType.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = Spacing.Sm)
                        )
                        Text(
                            text = "${item.noteCount} 条笔记",
                            style = ZhiLuType.meta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { targetId?.let(onConfirm) },
                enabled = targetId != null
            ) {
                Text("合并")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
