package com.example.zhilu.ui.note.blocks

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.ui.note.TodoBlock

@Composable
fun BlockContent(
    block: Block,
    isEditing: Boolean,
    onValueChange: (String) -> Unit,
    onLanguageClick: () -> Unit,
    modifier: Modifier = Modifier,
    todoItems: List<TodoItem>? = null,
    showCompletedTodos: Boolean = false,
    onCreateTodo: (suspend (String, Long?) -> Boolean)? = null,
    onUpdateTodo: ((TodoItem) -> Unit)? = null,
    onCompleteTodo: ((Long) -> Unit)? = null,
    onToggleCompletedTodos: (() -> Unit)? = null
) {
    when (block.type) {
        BlockType.TEXT -> {
            if (isEditing) {
                TextBlockEditor(
                    value = block.content,
                    onValueChange = onValueChange,
                    modifier = modifier
                )
            } else {
                Text(
                    text = block.content.ifBlank { " " },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = modifier.fillMaxWidth()
                )
            }
        }
        BlockType.CODE -> {
            if (isEditing) {
                CodeBlockEditor(
                    value = block.content,
                    language = block.language,
                    onValueChange = onValueChange,
                    onLanguageClick = onLanguageClick,
                    modifier = modifier
                )
            } else {
                ReadOnlyCodeBlockContent(
                    value = block.content,
                    language = block.language,
                    modifier = modifier
                )
            }
        }
        BlockType.LINK -> {
            if (isEditing) {
                LinkBlockEditor(
                    value = block.content,
                    onValueChange = onValueChange,
                    modifier = modifier
                )
            } else {
                Text(
                    text = block.content.ifBlank { " " },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = modifier.fillMaxWidth()
                )
            }
        }
        BlockType.LATEX -> {
            if (isEditing) {
                LatexBlockEditor(
                    value = block.content,
                    onValueChange = onValueChange,
                    modifier = modifier
                )
            } else {
                ReadOnlyLatexBlockContent(value = block.content, modifier = modifier)
            }
        }
        BlockType.IMAGE -> ImageBlockView(value = block.content, modifier = modifier)
        BlockType.DIVIDER -> DividerBlockView(readOnly = !isEditing, modifier = modifier)
        BlockType.TODO -> TodoBlockContent(
            block = block,
            isEditing = isEditing,
            modifier = modifier,
            todoItems = todoItems,
            showCompletedTodos = showCompletedTodos,
            onCreateTodo = onCreateTodo,
            onUpdateTodo = onUpdateTodo,
            onCompleteTodo = onCompleteTodo,
            onToggleCompletedTodos = onToggleCompletedTodos
        )
    }
}

@Composable
private fun TodoBlockContent(
    block: Block,
    isEditing: Boolean,
    modifier: Modifier,
    todoItems: List<TodoItem>?,
    showCompletedTodos: Boolean,
    onCreateTodo: (suspend (String, Long?) -> Boolean)?,
    onUpdateTodo: ((TodoItem) -> Unit)?,
    onCompleteTodo: ((Long) -> Unit)?,
    onToggleCompletedTodos: (() -> Unit)?
) {
    if (
        todoItems != null &&
        onCreateTodo != null &&
        onUpdateTodo != null &&
        onCompleteTodo != null &&
        onToggleCompletedTodos != null
    ) {
        TodoBlock(
            todoItems = todoItems,
            showCompletedTodos = showCompletedTodos,
            onCreateTodo = onCreateTodo,
            onUpdateTodo = onUpdateTodo,
            onCompleteTodo = onCompleteTodo,
            onToggleCompletedTodos = onToggleCompletedTodos,
            modifier = modifier,
            readOnly = !isEditing
        )
    } else {
        Text(
            text = block.content.ifBlank { "TODO" },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth()
        )
    }
}
