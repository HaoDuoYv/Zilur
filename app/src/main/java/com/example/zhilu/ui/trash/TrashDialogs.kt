package com.example.zhilu.ui.trash

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.zhilu.ui.component.AppButton
import com.example.zhilu.ui.component.AppButtonVariant
import com.example.zhilu.ui.component.AppDialog

/** 永久删除单条笔记的二次确认。 */
@Composable
fun DeleteForeverDialog(
    noteTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = "永久删除？",
        confirmButton = {
            AppButton(onClick = onConfirm, variant = AppButtonVariant.Danger) { Text("永久删除") }
        },
        dismissButton = {
            AppButton(onClick = onDismiss, variant = AppButtonVariant.Quiet) { Text("取消") }
        }
    ) {
        Text("「${noteTitle.ifBlank { "未命名知识" }}」将被彻底移除，无法恢复。")
    }
}

/** 清空回收站的二次确认。 */
@Composable
fun ClearTrashDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = "清空回收站？",
        confirmButton = {
            AppButton(onClick = onConfirm, variant = AppButtonVariant.Danger) { Text("清空") }
        },
        dismissButton = {
            AppButton(onClick = onDismiss, variant = AppButtonVariant.Quiet) { Text("取消") }
        }
    ) {
        Text("将永久删除全部 $count 条笔记，无法恢复。")
    }
}
