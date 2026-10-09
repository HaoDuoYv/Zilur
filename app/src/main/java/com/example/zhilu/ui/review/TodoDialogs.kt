package com.example.zhilu.ui.review

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import com.example.zhilu.domain.model.TodoItem

/**
 * 删除待办：待办**没有回收站**，删除不可逆 —— 比删笔记更需要一次确认。
 * 文案里带出待办文本，帮用户核对删的是哪一条。
 */
@Composable
fun TodoDeleteDialog(
    todo: TodoItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除这条待办？") },
        text = {
            Text(
                text = "「${todo.content}」将被删除，无法恢复。",
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("删除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
