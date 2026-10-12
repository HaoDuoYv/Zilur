package com.example.zhilu.ui.home

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.zhilu.ui.component.AppButton
import com.example.zhilu.ui.component.AppButtonVariant
import com.example.zhilu.ui.component.AppDialog

/** 移入回收站的确认。 */
@Composable
fun DeleteNoteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = "移入回收站？",
        confirmButton = {
            AppButton(onClick = onConfirm, variant = AppButtonVariant.Danger) { Text("删除") }
        },
        dismissButton = {
            AppButton(onClick = onDismiss, variant = AppButtonVariant.Quiet) { Text("取消") }
        }
    ) {
        Text("删除后可在回收站恢复，30 天后自动清空。")
    }
}
