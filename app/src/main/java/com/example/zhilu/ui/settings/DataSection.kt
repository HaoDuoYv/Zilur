package com.example.zhilu.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.zhilu.domain.usecase.ImportKnowledgeUseCase
import com.example.zhilu.ui.component.MetaLine
import com.example.zhilu.ui.theme.Spacing

/** 数据：统计 + 导出 / 导入。导入导出只此一处，不再散落到首页。 */
@Composable
fun DataSection(
    noteCount: Int,
    tagCount: Int,
    mediaCount: Int,
    totalMediaSize: Long,
    onExportJson: () -> Unit,
    onExportMarkdown: () -> Unit,
    onImportJson: () -> Unit,
    onImportDtk: () -> Unit
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
        SettingsRow(
            title = "导入备份",
            description = "从 JSON 备份恢复全部笔记",
            leadingIcon = Icons.Outlined.Upload,
            onClick = onImportJson,
            trailing = { Chevron() }
        )
        SettingsRow(
            title = "导入 .dtk 知识点",
            description = "导入单篇 .dtk 文件（含图片）",
            leadingIcon = Icons.Outlined.InsertDriveFile,
            onClick = onImportDtk,
            trailing = { Chevron() }
        )
    }
}

/** .dtk 导入前的预览确认。 */
@Composable
fun ImportPreviewDialog(
    preview: ImportKnowledgeUseCase.Preview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入知识点") },
        text = {
            Text(
                "标题：${preview.title}\n" +
                    "块数：${preview.blockCount}\n" +
                    "图片：${preview.imageCount}"
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("导入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
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