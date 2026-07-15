package com.example.zhilu.ui.note.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.TodoItem

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
    showTopDivider: Boolean = false,
    modifier: Modifier = Modifier,
    todoItems: List<TodoItem>? = null,
    showCompletedTodos: Boolean = false,
    onCreateTodo: (suspend (String, Long?) -> Boolean)? = null,
    onUpdateTodo: ((TodoItem) -> Unit)? = null,
    onCompleteTodo: ((Long) -> Unit)? = null,
    onToggleCompletedTodos: (() -> Unit)? = null,
    onImageClick: (() -> Unit)? = null,
    branchChildBlocks: List<Block> = emptyList(),
    isBranchExpanded: Boolean = false,
    onBranchTitleChange: (String) -> Unit = {},
    onToggleBranchExpanded: () -> Unit = {},
    onBranchChildValueChange: (Long, String) -> Unit = { _, _ -> },
    onBranchChildLanguageClick: (Long) -> Unit = {},
    onRemoveBranchChild: (Long) -> Unit = {},
    onAddBranchChild: (BlockType) -> Unit = {},
    onInsertAbove: () -> Unit = {},
    onInsertBelow: () -> Unit = {},
    onCopy: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
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

    var showTopIndicator by remember(block.id, index) { mutableStateOf(false) }
    var showBottomIndicator by remember(block.id, index) { mutableStateOf(false) }
    var showMenu by remember(block.id, index) { mutableStateOf(false) }

    val dragModifier = if (onDrag != null) {
        Modifier
            .pointerInput(block.id) {
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
            .semantics { contentDescription = "Long press to reorder block" }
            .zIndex(if (isDragging) 1f else 0f)
            .shadow(if (isDragging) 8.dp else 0.dp, MaterialTheme.shapes.medium)
    } else {
        Modifier
            .zIndex(if (isDragging) 1f else 0f)
            .shadow(if (isDragging) 8.dp else 0.dp, MaterialTheme.shapes.medium)
    }

    Column(
        modifier = modifier.then(dragModifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .padding(top = if (index == 0) 0.dp else (-8).dp)
                .pointerInput(Unit) {
                    detectTapGestures { showTopIndicator = !showTopIndicator }
                }
        ) {
            BlockInsertIndicator(
                visible = showTopIndicator,
                onClick = onInsertAbove,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showTopDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(block.id) {
                    detectTapGestures(
                        onLongPress = {
                            showMenu = true
                            onLongClick?.invoke()
                        }
                    )
                }
        ) {
            SwipeToDismissBox(
                state = dismissState,
                modifier = Modifier.fillMaxWidth(),
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
                    isDragging = isDragging,
                    todoItems = todoItems,
                    showCompletedTodos = showCompletedTodos,
                    onCreateTodo = onCreateTodo,
                    onUpdateTodo = onUpdateTodo,
                    onCompleteTodo = onCompleteTodo,
                    onToggleCompletedTodos = onToggleCompletedTodos,
                    onImageClick = onImageClick,
                    branchChildBlocks = branchChildBlocks,
                    isBranchExpanded = isBranchExpanded,
                    onBranchTitleChange = onBranchTitleChange,
                    onToggleBranchExpanded = onToggleBranchExpanded,
                    onBranchChildValueChange = onBranchChildValueChange,
                    onBranchChildLanguageClick = onBranchChildLanguageClick,
                    onRemoveBranchChild = onRemoveBranchChild,
                    onAddBranchChild = onAddBranchChild
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .padding(bottom = if (index == total - 1) 0.dp else (-8).dp)
                .pointerInput(Unit) {
                    detectTapGestures { showBottomIndicator = !showBottomIndicator }
                }
        ) {
            BlockInsertIndicator(
                visible = showBottomIndicator,
                onClick = onInsertBelow,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    BlockContextMenu(
        expanded = showMenu,
        onDismiss = { showMenu = false },
        canMoveUp = index > 0,
        canMoveDown = index < total - 1,
        onInsertAbove = onInsertAbove,
        onInsertBelow = onInsertBelow,
        onCopy = onCopy,
        onDelete = onRemove,
        onMoveUp = { onMoveUp?.invoke() },
        onMoveDown = { onMoveDown?.invoke() }
    )
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
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(end = 20.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "删除",
            tint = MaterialTheme.colorScheme.error
        )
    }
}
