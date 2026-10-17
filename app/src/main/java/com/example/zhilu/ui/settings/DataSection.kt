package com.example.zhilu.ui.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.zhilu.domain.usecase.ImportKnowledgeUseCase
import com.example.zhilu.ui.component.AppButton
import com.example.zhilu.ui.component.AppButtonVariant
import com.example.zhilu.ui.component.AppDialog

/**
 * .dtk 导入前的预览确认。一份包可以是单篇分享，也可以是全量/选中项备份。
 *
 * 原先与它同文件的 `DataSection` / `StorageSection` 已随「我的」页重排撤掉 ——
 * 导出与回收站现在并在「更多」组里，直接由 `SettingsScreen` 排版；
 * 这两个组件的职责已没有独立成文件的价值，故不保留壳。
 */
@Composable
fun ImportPreviewDialog(
    preview: ImportKnowledgeUseCase.Preview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val multiple = preview.noteCount > 1
    AppDialog(
        onDismissRequest = onDismiss,
        title = if (multiple) "导入 ${preview.noteCount} 篇笔记" else "导入知识点",
        confirmButton = {
            AppButton(onClick = onConfirm) { Text("导入") }
        },
        dismissButton = {
            AppButton(onClick = onDismiss, variant = AppButtonVariant.Quiet) { Text("取消") }
        }
    ) {
        Text(
            if (multiple) {
                "共 ${preview.noteCount} 篇\n块数：${preview.blockCount}\n图片：${preview.imageCount}"
            } else {
                "标题：${preview.title}\n块数：${preview.blockCount}\n图片：${preview.imageCount}"
            }
        )
    }
}
