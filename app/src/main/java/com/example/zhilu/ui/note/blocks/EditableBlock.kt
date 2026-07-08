package com.example.zhilu.ui.note.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.ui.note.theme.NoteColors

@Composable
fun EditableBlock(
    index: Int,
    block: Block,
    total: Int,
    onValueChange: (String) -> Unit,
    onLanguageClick: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onDrag: ((offsetY: Float) -> Unit)? = null,
    onDragStart: (() -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier,
    todoItems: List<TodoItem>? = null,
    showCompletedTodos: Boolean = false,
    onCreateTodo: (suspend (String, Long?) -> Boolean)? = null,
    onUpdateTodo: ((TodoItem) -> Unit)? = null,
    onCompleteTodo: ((Long) -> Unit)? = null,
    onToggleCompletedTodos: (() -> Unit)? = null
) {
    val currentOnRemove by rememberUpdatedState(onRemove)
    var removeRequested by remember(block.id, index) { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { direction ->
            if (shouldRequestSwipeDelete(direction)) {
                if (!removeRequested) {
                    removeRequested = true
                    currentOnRemove()
                }
            }
            shouldConfirmSwipeValueChange(direction)
        }
    )

    LaunchedEffect(removeRequested, block.id, index) {
        if (removeRequested) {
            dismissState.reset()
            removeRequested = false
        }
    }

    val dragModifier = if (onDrag != null) {
        Modifier
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart?.invoke() },
                    onDragEnd = { onDragEnd?.invoke() },
                    onDragCancel = { onDragEnd?.invoke() },
                    onDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: androidx.compose.ui.geometry.Offset ->
                        change.consume()
                        onDrag(dragAmount.y)
                    }
                )
            }
            .zIndex(if (isDragging) 1f else 0f)
            .shadow(if (isDragging) 8.dp else 0.dp)
    } else {
        Modifier
            .zIndex(if (isDragging) 1f else 0f)
            .shadow(if (isDragging) 8.dp else 0.dp)
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.then(dragModifier),
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = { DeleteBackground() }
    ) {
        BlockCard(
            block = block,
            isEditing = true,
            onValueChange = onValueChange,
            onLanguageClick = onLanguageClick,
            onDelete = onRemove,
            onMoveUp = onMoveUp.takeIf { index > 0 },
            onMoveDown = onMoveDown.takeIf { index < total - 1 },
            todoItems = todoItems,
            showCompletedTodos = showCompletedTodos,
            onCreateTodo = onCreateTodo,
            onUpdateTodo = onUpdateTodo,
            onCompleteTodo = onCompleteTodo,
            onToggleCompletedTodos = onToggleCompletedTodos
        )
    }
}

fun shouldRequestSwipeDelete(direction: SwipeToDismissBoxValue): Boolean =
    direction == SwipeToDismissBoxValue.EndToStart

fun shouldConfirmSwipeValueChange(direction: SwipeToDismissBoxValue): Boolean =
    when (direction) {
        SwipeToDismissBoxValue.EndToStart -> false
        SwipeToDismissBoxValue.StartToEnd -> false
        SwipeToDismissBoxValue.Settled -> true
    }

@Composable
private fun DeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(NoteColors.deleteBackground)
            .padding(end = 20.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "删除",
            tint = NoteColors.deleteIcon
        )
    }
}
