package com.example.zhilu.ui.note.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.BlockType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockTypePickerSheet(
    onDismiss: () -> Unit,
    onSelect: (BlockType) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "插入内容块",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            listOf(
                BlockType.TEXT to Icons.AutoMirrored.Filled.Article,
                BlockType.CODE to Icons.Default.DataObject,
                BlockType.LINK to Icons.Default.Link,
                BlockType.LATEX to Icons.Default.Calculate,
                BlockType.BRANCH to Icons.AutoMirrored.Filled.CallSplit
            ).forEach { (type, icon) ->
                BlockTypeOption(
                    label = blockTypeLabel(type),
                    icon = icon,
                    onClick = { onSelect(type) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastePositionSheet(
    onDismiss: () -> Unit,
    onPasteTop: () -> Unit,
    onPasteBottom: () -> Unit,
    onPasteEnd: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "粘贴到",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            Text(
                text = "粘贴到本卡片末尾",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPasteEnd)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )
            Text(
                text = "粘贴到卡片开头",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPasteTop)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )
            Text(
                text = "粘贴到卡片末尾块之后",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPasteBottom)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }
    }
}

@Composable
private fun BlockTypeOption(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
