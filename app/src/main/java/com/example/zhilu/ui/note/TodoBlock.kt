package com.example.zhilu.ui.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.TodoItem
import kotlinx.coroutines.launch

@Composable
fun TodoBlock(
    todoItems: List<TodoItem>,
    showCompletedTodos: Boolean,
    onCreateTodo: suspend (String, Long?) -> Boolean,
    onUpdateTodo: (TodoItem) -> Unit,
    onCompleteTodo: (Long) -> Unit,
    onToggleCompletedTodos: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false
) {
    val coroutineScope = rememberCoroutineScope()
    var content by remember { mutableStateOf("") }
    var reminderText by remember { mutableStateOf("") }
    val visibleItems = if (showCompletedTodos) {
        todoItems
    } else {
        todoItems.filterNot { it.isCompleted }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!readOnly) {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("TODO") },
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = reminderText,
                    onValueChange = { value -> reminderText = value.filter(Char::isDigit) },
                    modifier = Modifier.weight(1f),
                    label = { Text("提醒时间") },
                    singleLine = true
                )
                Button(
                    onClick = {
                        coroutineScope.launch {
                            if (onCreateTodo(content, reminderText.toLongOrNull())) {
                                content = ""
                                reminderText = ""
                            }
                        }
                    }
                ) {
                    Text("添加")
                }
            }
        }

        if (todoItems.any { it.isCompleted }) {
            TextButton(onClick = onToggleCompletedTodos) {
                Text(if (showCompletedTodos) "隐藏已完成" else "显示已完成")
            }
        }

        if (visibleItems.isEmpty()) {
            Text(
                text = if (todoItems.isEmpty()) "暂无 TODO" else "已隐藏完成项",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            visibleItems.forEach { todo ->
                TodoRow(
                    todo = todo,
                    enabled = !readOnly,
                    onUpdateTodo = onUpdateTodo,
                    onCompleteTodo = onCompleteTodo
                )
            }
        }
    }
}

@Composable
private fun TodoRow(
    todo: TodoItem,
    enabled: Boolean,
    onUpdateTodo: (TodoItem) -> Unit,
    onCompleteTodo: (Long) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = todo.isCompleted,
            onCheckedChange = if (enabled) {
                { checked ->
                    if (checked) {
                        onCompleteTodo(todo.id)
                    } else {
                        onUpdateTodo(todo.copy(completedAt = null))
                    }
                }
            } else {
                null
            }
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = todo.content,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                color = if (todo.isCompleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            todo.remindAt?.let { remindAt ->
                Text(
                    text = remindAt.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
