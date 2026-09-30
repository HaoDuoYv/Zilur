package com.example.zhilu.ui.note.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.AnnotatedString
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.TodoItem

@Composable
fun ReadOnlyBlock(
    block: Block,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    onLanguageClick: () -> Unit = {},
    showTopDivider: Boolean = false,
    todoItems: List<TodoItem>? = null,
    showCompletedTodos: Boolean = false,
    onCreateTodo: (suspend (String, Long?) -> Boolean)? = null,
    onUpdateTodo: ((TodoItem) -> Unit)? = null,
    onCompleteTodo: ((Long) -> Unit)? = null,
    onToggleCompletedTodos: (() -> Unit)? = null,
    onImageClick: (() -> Unit)? = null,
    branchChildBlocks: List<Block> = emptyList(),
    isBranchExpanded: Boolean = false,
    onToggleBranchExpanded: () -> Unit = {},
    onToggleCompletedTodosInBranch: (() -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    val currentOnCopy by rememberUpdatedState(onCopy)
    var copyMenuExpanded by remember(block.id) { mutableStateOf(false) }

    Box(
        modifier = modifier.pointerInput(block.id, block.content) {
            detectTapGestures(
                onLongPress = { copyMenuExpanded = true }
            )
        }
    ) {
        Column {
            if (showTopDivider) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                )
            }
            BlockCard(
                block = block,
                isEditing = false,
                onLanguageClick = onLanguageClick,
                modifier = Modifier.fillMaxWidth(),
                todoItems = todoItems,
                showCompletedTodos = showCompletedTodos,
                onCreateTodo = onCreateTodo,
                onUpdateTodo = onUpdateTodo,
                onCompleteTodo = onCompleteTodo,
                onToggleCompletedTodos = onToggleCompletedTodos,
                onImageClick = onImageClick,
                branchChildBlocks = branchChildBlocks,
                isBranchExpanded = isBranchExpanded,
                onToggleBranchExpanded = onToggleBranchExpanded
            )
        }
        DropdownMenu(
            expanded = copyMenuExpanded,
            onDismissRequest = { copyMenuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(readOnlyBlockCopyMenuLabel()) },
                onClick = {
                    copyMenuExpanded = false
                    clipboardManager.setText(readOnlyBlockClipboardText(block))
                    currentOnCopy()
                }
            )
        }
    }
}

fun readOnlyBlockCopyMenuLabel(): String = "复制此块"

fun readOnlyBlockClipboardText(block: Block): AnnotatedString {
    val text = when (block.type) {
        com.example.zhilu.domain.model.BlockType.IMAGE -> "[图片]"
        com.example.zhilu.domain.model.BlockType.DIVIDER -> "----"
        com.example.zhilu.domain.model.BlockType.BRANCH ->
            block.content.ifBlank { "[分支]" }
        com.example.zhilu.domain.model.BlockType.LATEX ->
            block.content.ifBlank { "[公式]" }
        else -> block.content
    }
    return AnnotatedString(text)
}
