package com.example.zhilu.ui.note.toolbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.note.theme.NoteColors

@Composable
fun BlockToolbar(
    onAddText: () -> Unit,
    onAddCode: () -> Unit,
    onAddImage: () -> Unit,
    onAddLink: () -> Unit,
    onAddLatex: () -> Unit,
    onAddDivider: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomEnd
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAddText) {
                Icon(
                    imageVector = Icons.Default.TextFields,
                    contentDescription = "文字"
                )
            }
            IconButton(onClick = onAddCode) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = "代码"
                )
            }
            IconButton(onClick = onAddImage) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "图片"
                )
            }
        }

        Box {
            FloatingActionButton(
                onClick = { menuExpanded = true },
                containerColor = NoteColors.primaryIndigo,
                contentColor = NoteColors.primaryIndigoLight
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加"
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("链接") },
                    onClick = {
                        menuExpanded = false
                        onAddLink()
                    }
                )
                DropdownMenuItem(
                    text = { Text("公式") },
                    onClick = {
                        menuExpanded = false
                        onAddLatex()
                    }
                )
                DropdownMenuItem(
                    text = { Text("分割线") },
                    onClick = {
                        menuExpanded = false
                        onAddDivider()
                    }
                )
            }
        }
    }
}
