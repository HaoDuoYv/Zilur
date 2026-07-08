package com.example.zhilu.ui.note.blocks

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.text.AnnotatedString
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.TodoItem

@Composable
fun ReadOnlyBlock(
    block: Block,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    onLanguageClick: () -> Unit = {},
    todoItems: List<TodoItem>? = null,
    showCompletedTodos: Boolean = false,
    onCreateTodo: (suspend (String, Long?) -> Boolean)? = null,
    onUpdateTodo: ((TodoItem) -> Unit)? = null,
    onCompleteTodo: ((Long) -> Unit)? = null,
    onToggleCompletedTodos: (() -> Unit)? = null
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
            onToggleCompletedTodos = onToggleCompletedTodos
        )
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

fun readOnlyBlockClipboardText(block: Block): AnnotatedString = AnnotatedString(block.content)
