package com.example.zhilu.ui.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.zhilu.domain.model.Tag

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
