package com.example.zhilu.ui.review

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.ui.component.AppButton
import com.example.zhilu.ui.component.AppButtonVariant
import com.example.zhilu.ui.component.AppDialog

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
    AppDialog(
        onDismissRequest = onDismiss,
        title = "删除这条待办？",
        confirmButton = {
            AppButton(onClick = onConfirm, variant = AppButtonVariant.Danger) { Text("删除") }
        },
        dismissButton = {
            AppButton(onClick = onDismiss, variant = AppButtonVariant.Quiet) { Text("取消") }
        }
    ) {
        Text(
            text = "「${todo.content}」将被删除，无法恢复。",
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
