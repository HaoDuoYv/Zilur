package com.example.zhilu.ui.note.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.ui.component.ElevationTokens
import com.example.zhilu.ui.theme.AlphaTokens

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
    /** 是否为当前激活块（决定是否显示类型图标、⋮ 与拖拽把手）。 */
    isActive: Boolean = false,
    /** 长按/交互时将本块置为激活块。 */
    onActivate: () -> Unit = {},
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
    onLongClick: (() -> Unit)? = null,
    onCiteToAi: () -> Unit = {},
    isGenerating: Boolean = false
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
    var menuOffset by remember(block.id, index) { mutableStateOf(DpOffset.Zero) }
    val density = LocalDensity.current

    val tapModifier = Modifier.pointerInput(block.id) {
        detectTapGestures(
            // 注意：这里**不能**加 onTap 来激活块。块外层是 SwipeToDismissBox，
            // 内层再消费一次指针事件会让横向滑动手势错乱（真机上出现过"轻点一下
            // 就把删除红底拉出来且卡住"）。激活改由 gutter 的序号承担（见 BlockGutter）。
            onLongPress = { offsetPx ->
                menuOffset = with(density) {
                    DpOffset(offsetPx.x.toDp(), offsetPx.y.toDp())
                }
                showMenu = true
                onActivate()
                onLongClick?.invoke()
            }
        )
    }

    // 拖拽手势只挂在把手上（激活块才露出），长按菜单因此不被抢占。
    val dragHandleModifier = if (onDrag != null) {
        Modifier
            .pointerInput(block.id) {
                detectDragGestures(
                    onDragStart = { onDragStart?.invoke() },
                    onDragEnd = { onDragEnd?.invoke() },
                    onDragCancel = { onDragEnd?.invoke() },
                    onDrag = { change: PointerInputChange, dragAmount: Offset ->
                        change.consume()
                        onDrag(dragAmount.y)
                    }
                )
            }
            .semantics { contentDescription = "拖动排序" }
    } else {
        Modifier
    }

    val dragModifier = Modifier
        .zIndex(if (isDragging) 1f else 0f)
        .shadow(if (isDragging) ElevationTokens.Overlay else 0.dp, MaterialTheme.shapes.medium)

    Box(modifier = modifier.then(dragModifier)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(tapModifier)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
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

            Box(
                modifier = Modifier.fillMaxWidth()
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
                        isEditing = !isGenerating,
                        onValueChange = onValueChange,
                        onLanguageClick = onLanguageClick,
                        onDelete = onRemove,
                        onMoveUp = onMoveUp.takeIf { index > 0 },
                        onMoveDown = onMoveDown.takeIf { index < total - 1 },
                        isDragging = isDragging,
                        isActive = isActive,
                        showDragHandle = isActive && onDrag != null,
                        dragHandleModifier = dragHandleModifier,
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
            offset = menuOffset,
            canMoveUp = index > 0,
            canMoveDown = index < total - 1,
            onInsertAbove = onInsertAbove,
            onInsertBelow = onInsertBelow,
            onCopy = onCopy,
            onDelete = onRemove,
            onMoveUp = { onMoveUp?.invoke() },
            onMoveDown = { onMoveDown?.invoke() },
            onCiteToAi = onCiteToAi
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
