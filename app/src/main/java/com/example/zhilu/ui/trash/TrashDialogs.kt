package com.example.zhilu.ui.trash

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** 永久删除单条笔记的二次确认。 */
@Composable
fun DeleteForeverDialog(
    noteTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("永久删除？") },
        text = { Text("「${noteTitle.ifBlank { "未命名知识" }}」将被彻底移除，无法恢复。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("永久删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 清空回收站的二次确认。 */
@Composable
fun ClearTrashDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清空回收站？") },
        text = { Text("将永久删除全部 $count 条笔记，无法恢复。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("清空", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}