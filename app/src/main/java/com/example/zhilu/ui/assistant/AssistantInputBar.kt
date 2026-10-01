package com.example.zhilu.ui.assistant

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.zhilu.ui.theme.Radius
import com.example.zhilu.ui.theme.ZhiLuType

@Composable
fun AssistantInputBar(
    text: String,
    attachedImages: List<String>,
    attachedFile: AttachedFile?,
    isGenerating: Boolean,
    onTextChange: (String) -> Unit,
    onPickImages: () -> Unit,
    onRemoveImage: (Int) -> Unit,
    onPickFile: () -> Unit,
    onRemoveFile: () -> Unit,
    onSend: () -> Unit
) {
    var attachMenuOpen by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .imePadding()
        ) {
            if (attachedImages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp)
                ) {
                    attachedImages.forEachIndexed { index, uri ->
                        ImageThumb(uri = uri, onRemove = { onRemoveImage(index) })
                    }
                }
            }
            attachedFile?.let { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = onRemoveFile, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "移除文件",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 附件入口收进前导 ＋ 菜单，主行只留「输入 + 发送」。
                Box {
                    IconButton(
                        onClick = { attachMenuOpen = true },
                        enabled = !isGenerating
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加附件",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = attachMenuOpen,
                        onDismissRequest = { attachMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("添加图片") },
                            leadingIcon = {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                            },
                            onClick = {
                                attachMenuOpen = false
                                onPickImages()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("添加文件") },
                            leadingIcon = {
                                Icon(Icons.Default.AttachFile, contentDescription = null)
                            },
                            onClick = {
                                attachMenuOpen = false
                                onPickFile()
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("问问 AI 助手，或发图识别文字…", style = ZhiLuType.bodySmall) },
                    textStyle = ZhiLuType.bodySmall,
                    shape = RoundedCornerShape(Radius.Field),
                    maxLines = 4
                )
                val canSend = (text.isNotBlank() || attachedImages.isNotEmpty() || attachedFile != null) && !isGenerating
                Surface(
                    onClick = onSend,
                    enabled = canSend,
                    shape = CircleShape,
                    color = if (canSend) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (canSend) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageThumb(uri: String, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(64.dp)
    ) {
        SubcomposeAsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "移除图片",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
