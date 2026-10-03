package com.example.zhilu.ui.create

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 底栏中央 ＋ 弹出的「新建 / 导入」弹层。
 *
 * 四个入口按设计原型排成 2×2：新建空白 / 导入备份 / 导入 .dtk / AI 创建。
 * 每一项都是「图标块 + 标题 + 一句说明」——说明写的是"这个入口会把什么带进知识库"，
 * 因为四个入口的差别只在来源，标题本身不足以区分。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSheet(
    onDismiss: () -> Unit,
    onBlank: () -> Unit,
    onImportJson: () -> Unit,
    onImportDtk: () -> Unit,
    onAiCreate: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Md)
                .padding(bottom = Spacing.Lg)
        ) {
            Text(
                text = "新建 / 导入",
                style = ZhiLuType.rowTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Spacing.Sm))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                CreateOption(
                    icon = Icons.Default.Edit,
                    title = "新建空白",
                    description = "从零开始记录",
                    onClick = onBlank,
                    modifier = Modifier.weight(1f)
                )
                CreateOption(
                    icon = Icons.Default.AutoAwesome,
                    title = "AI 创建",
                    description = "去助手描述要什么",
                    onClick = onAiCreate,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(Spacing.Sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                CreateOption(
                    icon = Icons.Default.FileDownload,
                    title = "导入备份",
                    description = "从 JSON 备份恢复全部笔记",
                    onClick = onImportJson,
                    modifier = Modifier.weight(1f)
                )
                CreateOption(
                    icon = Icons.Default.FolderZip,
                    title = "导入 .dtk",
                    description = "导入单篇知识点（含图片）",
                    onClick = onImportDtk,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CreateOption(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.Sm, vertical = Spacing.Sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.Xs)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = title,
            style = ZhiLuType.rowTitle,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = description,
            style = ZhiLuType.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
