package com.example.zhilu.ui.note.knowledge

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.KnowledgeCard
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.ui.note.blocks.EditableBlock
import com.example.zhilu.ui.note.blocks.ReadOnlyBlock
import com.example.zhilu.ui.note.blocks.blockTypeLabel

private const val FocusAnimationDurationMillis = 200

private val KnowledgeCardBackground = Color(0xFFFFFFFF)
private val KnowledgeCardOutlineDefault = Color(0xFFE5E7EB)
private val KnowledgeCardOutlineFocused = Color(0xFF3B82F6)
private val PinIconColor = Color(0xFF9CA3AF)

@Composable
fun KnowledgeCardItem(
    card: KnowledgeCard,
    isEditing: Boolean,
    canDelete: Boolean,
    onFocus: () -> Unit,
    onTitleChange: (String) -> Unit,
    onDelete: () -> Unit,
    onBlockContentChange: (Long, String) -> Unit,
    onBlockLanguageClick: (Long) -> Unit,
    onRemoveBlock: (Long) -> Unit,
    onMoveBlockUp: (Long) -> Unit,
    onMoveBlockDown: (Long) -> Unit,
    onInsertBlockAt: (Int, BlockType) -> Unit = { _, _ -> },
    onCopyBlock: (Long) -> Unit = { },
    onImageClick: (Block) -> Unit,
    onToggleBranchExpanded: (Long) -> Unit,
    onBranchTitleChange: (Long, String) -> Unit,
    onBranchChildValueChange: (Long, String) -> Unit,
    onBranchChildLanguageClick: (Long) -> Unit,
    onRemoveBranchChild: (Long) -> Unit,
    onAddBranchChild: (Long, BlockType) -> Unit,
    onAddBranchChildImage: (Long) -> Unit,
    branchExpandedStates: Map<Long, Boolean>,
    todoItems: List<TodoItem>,
    showCompletedTodos: Boolean,
    onCreateTodo: (suspend (String, Long?) -> Boolean)?,
    onUpdateTodo: ((TodoItem) -> Unit)?,
    onCompleteTodo: ((Long) -> Unit)?,
    onToggleCompletedTodos: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val isFocused = card.isFocused && isEditing

    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) 2.dp else 1.dp,
        animationSpec = tween(durationMillis = FocusAnimationDurationMillis),
        label = "KnowledgeCardBorderWidth"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) KnowledgeCardOutlineFocused else KnowledgeCardOutlineDefault,
        animationSpec = tween(durationMillis = FocusAnimationDurationMillis),
        label = "KnowledgeCardBorderColor"
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isFocused) 6.dp else 1.dp,
        animationSpec = tween(durationMillis = FocusAnimationDurationMillis),
        label = "KnowledgeCardShadowElevation"
    )

    val shape = MaterialTheme.shapes.medium

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(shadowElevation, shape)
            .border(borderWidth, borderColor, shape)
            .clickable(enabled = isEditing, onClick = onFocus),
        shape = shape,
        color = KnowledgeCardBackground
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CardTitleInput(
                title = card.title,
                onTitleChange = onTitleChange,
                readOnly = !(isEditing && isFocused),
                showDelete = isEditing && isFocused,
                canDelete = canDelete,
                onDelete = onDelete
            )

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            CardBlockList(
                card = card,
                isEditing = isEditing,
                onBlockContentChange = onBlockContentChange,
                onBlockLanguageClick = onBlockLanguageClick,
                onRemoveBlock = onRemoveBlock,
                onMoveBlockUp = onMoveBlockUp,
                onMoveBlockDown = onMoveBlockDown,
                onInsertBlockAt = onInsertBlockAt,
                onCopyBlock = onCopyBlock,
                onImageClick = onImageClick,
                onToggleBranchExpanded = onToggleBranchExpanded,
                onBranchTitleChange = onBranchTitleChange,
                onBranchChildValueChange = onBranchChildValueChange,
                onBranchChildLanguageClick = onBranchChildLanguageClick,
                onRemoveBranchChild = onRemoveBranchChild,
                onAddBranchChild = onAddBranchChild,
                onAddBranchChildImage = onAddBranchChildImage,
                branchExpandedStates = branchExpandedStates,
                todoItems = todoItems,
                showCompletedTodos = showCompletedTodos,
                onCreateTodo = onCreateTodo,
                onUpdateTodo = onUpdateTodo,
                onCompleteTodo = onCompleteTodo,
                onToggleCompletedTodos = onToggleCompletedTodos
            )
        }
    }
}

@Composable
private fun CardTitleInput(
    title: String,
    onTitleChange: (String) -> Unit,
    readOnly: Boolean,
    showDelete: Boolean,
    canDelete: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textStyle = MaterialTheme.typography.titleMedium.merge(
        TextStyle(color = MaterialTheme.colorScheme.onSurface)
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.PushPin,
            contentDescription = "知识小点",
            tint = PinIconColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        if (readOnly) {
            Text(
                text = title.ifBlank { "知识小点" },
                style = textStyle,
                modifier = Modifier.weight(1f)
            )
        } else {
            BasicTextField(
                value = title,
                onValueChange = onTitleChange,
                modifier = Modifier.weight(1f),
                textStyle = textStyle,
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (title.isEmpty()) {
                        Text(
                            text = "输入小点名称(如:情况一)...",
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    innerTextField()
                }
            )
        }

        if (showDelete) {
            IconButton(
                onClick = onDelete,
                enabled = canDelete
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = if (canDelete) "删除知识卡片" else "至少保留一张知识卡片",
                    tint = if (canDelete) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    }
                )
            }
        }
    }
}

@Composable
private fun CardBlockList(
    card: KnowledgeCard,
    isEditing: Boolean,
    onBlockContentChange: (Long, String) -> Unit,
    onBlockLanguageClick: (Long) -> Unit,
    onRemoveBlock: (Long) -> Unit,
    onMoveBlockUp: (Long) -> Unit,
    onMoveBlockDown: (Long) -> Unit,
    onInsertBlockAt: (Int, BlockType) -> Unit,
    onCopyBlock: (Long) -> Unit,
    onImageClick: (Block) -> Unit,
    onToggleBranchExpanded: (Long) -> Unit,
    onBranchTitleChange: (Long, String) -> Unit,
    onBranchChildValueChange: (Long, String) -> Unit,
    onBranchChildLanguageClick: (Long) -> Unit,
    onRemoveBranchChild: (Long) -> Unit,
    onAddBranchChild: (Long, BlockType) -> Unit,
    onAddBranchChildImage: (Long) -> Unit,
    branchExpandedStates: Map<Long, Boolean>,
    todoItems: List<TodoItem>,
    showCompletedTodos: Boolean,
    onCreateTodo: (suspend (String, Long?) -> Boolean)?,
    onUpdateTodo: ((TodoItem) -> Unit)?,
    onCompleteTodo: ((Long) -> Unit)?,
    onToggleCompletedTodos: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val topLevelBlocks = card.blocks.filter { it.parentBranchId == null }
    var pendingInsertIndex by remember { mutableIntStateOf(-1) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (topLevelBlocks.isEmpty()) {
            Text(
                text = "点击底部工具栏添加内容块",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        } else {
            topLevelBlocks.forEachIndexed { index, block ->
                val prevType = topLevelBlocks.getOrNull(index - 1)?.type
                val topPadding = when {
                    block.type == BlockType.DIVIDER || prevType == BlockType.DIVIDER -> 24.dp
                    prevType != null && prevType == block.type -> 8.dp
                    prevType != null -> 16.dp
                    else -> 0.dp
                }
                val showTopDivider = prevType != null &&
                    prevType == block.type &&
                    block.type != BlockType.DIVIDER &&
                    block.type != BlockType.IMAGE &&
                    block.type != BlockType.LATEX
                val childBlocks = card.blocks.filter { it.parentBranchId == block.id }
                val isBranchExpanded = if (isEditing) {
                    branchExpandedStates[block.id] ?: false
                } else {
                    true
                }
                val blockIndexInAll = card.blocks.indexOfFirst { it.id == block.id }
                val childCount = card.blocks.count { it.parentBranchId == block.id }

                val canEditBlock = isEditing && card.isFocused
                if (canEditBlock) {
                    EditableBlock(
                        index = index,
                        block = block,
                        total = topLevelBlocks.size,
                        onValueChange = { onBlockContentChange(block.id, it) },
                        onLanguageClick = { onBlockLanguageClick(block.id) },
                        onRemove = { onRemoveBlock(block.id) },
                        onMoveUp = { topLevelBlocks.getOrNull(index - 1)?.id?.let { onMoveBlockUp(block.id) } },
                        onMoveDown = { topLevelBlocks.getOrNull(index + 1)?.id?.let { onMoveBlockDown(block.id) } },
                        onInsertAbove = { pendingInsertIndex = blockIndexInAll },
                        onInsertBelow = { pendingInsertIndex = blockIndexInAll + 1 + childCount },
                        onCopy = { onCopyBlock(block.id) },
                        onImageClick = if (block.type == BlockType.IMAGE) {
                            { onImageClick(block) }
                        } else {
                            null
                        },
                        showTopDivider = showTopDivider,
                        modifier = Modifier.padding(top = topPadding),
                        todoItems = todoItems,
                        showCompletedTodos = showCompletedTodos,
                        onCreateTodo = onCreateTodo,
                        onUpdateTodo = onUpdateTodo,
                        onCompleteTodo = onCompleteTodo,
                        onToggleCompletedTodos = onToggleCompletedTodos,
                        branchChildBlocks = childBlocks,
                        isBranchExpanded = isBranchExpanded,
                        onBranchTitleChange = { onBranchTitleChange(block.id, it) },
                        onToggleBranchExpanded = { onToggleBranchExpanded(block.id) },
                        onBranchChildValueChange = { childId, value ->
                            onBranchChildValueChange(childId, value)
                        },
                        onBranchChildLanguageClick = { childId ->
                            onBranchChildLanguageClick(childId)
                        },
                        onRemoveBranchChild = { childId -> onRemoveBranchChild(childId) },
                        onAddBranchChild = { type ->
                            if (type == BlockType.IMAGE) {
                                onAddBranchChildImage(block.id)
                            } else {
                                onAddBranchChild(block.id, type)
                            }
                        }
                    )
                } else {
                    ReadOnlyBlock(
                        block = block,
                        onCopy = {},
                        showTopDivider = showTopDivider,
                        modifier = Modifier.padding(top = topPadding),
                        todoItems = todoItems,
                        showCompletedTodos = showCompletedTodos,
                        onToggleCompletedTodos = onToggleCompletedTodos,
                        onImageClick = if (block.type == BlockType.IMAGE) {
                            { onImageClick(block) }
                        } else {
                            null
                        },
                        branchChildBlocks = childBlocks,
                        isBranchExpanded = isBranchExpanded,
                        onToggleBranchExpanded = { onToggleBranchExpanded(block.id) }
                    )
                }
            }
        }
    }

    if (pendingInsertIndex >= 0) {
        AlertDialog(
            onDismissRequest = { pendingInsertIndex = -1 },
            title = { Text("选择块类型") },
            text = {
                Column {
                    listOf(
                        BlockType.TEXT,
                        BlockType.CODE,
                        BlockType.LINK,
                        BlockType.LATEX,
                        BlockType.BRANCH
                    ).forEach { type ->
                        TextButton(
                            onClick = {
                                onInsertBlockAt(pendingInsertIndex, type)
                                pendingInsertIndex = -1
                            }
                        ) {
                            Text(blockTypeLabel(type))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingInsertIndex = -1 }) {
                    Text("取消")
                }
            }
        )
    }
}
