package com.example.zhilu.ui.note.blocks

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun BlockContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onInsertAbove: () -> Unit,
    onInsertBelow: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("在上方插入") },
            onClick = { onDismiss(); onInsertAbove() }
        )
        DropdownMenuItem(
            text = { Text("在下方插入") },
            onClick = { onDismiss(); onInsertBelow() }
        )
        DropdownMenuItem(
            text = { Text("复制") },
            onClick = { onDismiss(); onCopy() }
        )
        DropdownMenuItem(
            text = { Text("删除") },
            onClick = { onDismiss(); onDelete() }
        )
        DropdownMenuItem(
            text = { Text("上移") },
            onClick = { onDismiss(); onMoveUp() },
            enabled = canMoveUp
        )
        DropdownMenuItem(
            text = { Text("下移") },
            onClick = { onDismiss(); onMoveDown() },
            enabled = canMoveDown
        )
    }
}
