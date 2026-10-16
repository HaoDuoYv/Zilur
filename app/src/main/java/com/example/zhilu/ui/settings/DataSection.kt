package com.example.zhilu.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.zhilu.domain.usecase.ImportKnowledgeUseCase
import com.example.zhilu.ui.component.AppButton
import com.example.zhilu.ui.component.AppButtonVariant
import com.example.zhilu.ui.component.AppDialog
import com.example.zhilu.ui.component.MetaLine
import com.example.zhilu.ui.theme.Spacing

/** 数据：统计 + 导出。**导入不在这里**——见 [DataSection] 上方说明。 */
@Composable
fun DataSection(
    noteCount: Int,
    tagCount: Int,
    mediaCount: Int,
    totalMediaSize: Long,
    onExportJson: () -> Unit,
    onExportMarkdown: () -> Unit
) {
    SettingsGroup(title = "数据") {
        MetaLine(
            parts = listOf(
                "$noteCount 条笔记",
                "$tagCount 个标签",
                "$mediaCount 个媒体 · ${formatBytes(totalMediaSize)}"
            ),
            modifier = Modifier.padding(vertical = Spacing.Xs)
        )
        SettingsRow(
            title = "导出备份",
            description = "导出全部笔记为 JSON 备份文件",
            leadingIcon = Icons.Outlined.Download,
            onClick = onExportJson,
            trailing = { Chevron() }
        )
        SettingsRow(
            title = "导出 Markdown",
            description = "用于在其他编辑器继续写作",
            leadingIcon = Icons.Outlined.Description,
            onClick = onExportMarkdown,
            trailing = { Chevron() }
        )
    }
}

/** .dtk 导入前的预览确认。一份包可以是单篇分享，也可以是全量/选中项备份。 */
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

/** 存储：回收站入口单独成组使用。 */
@Composable
fun StorageSection(onOpenTrash: () -> Unit) {
    SettingsGroup(title = "存储") {
        SettingsRow(
            title = "回收站",
            description = "恢复或永久删除已移除的笔记",
            leadingIcon = Icons.Outlined.Delete,
            onClick = onOpenTrash,
            trailing = { Chevron() }
        )
    }
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    return String.format("%.1f MB", kb / 1024.0)
}