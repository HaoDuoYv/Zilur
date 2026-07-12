package com.example.zhilu.ui.note.blocks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.TodoItem

private val BlockTypeIconSize = 20.dp
private val BlockTypeIconSpacing = 8.dp

@Composable
fun BlockCard(
    block: Block,
    isEditing: Boolean,
    onValueChange: (String) -> Unit = {},
    onLanguageClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    isDragging: Boolean = false,
    todoItems: List<TodoItem>? = null,
    showCompletedTodos: Boolean = false,
    onCreateTodo: (suspend (String, Long?) -> Boolean)? = null,
    onUpdateTodo: ((TodoItem) -> Unit)? = null,
    onCompleteTodo: ((Long) -> Unit)? = null,
    onToggleCompletedTodos: (() -> Unit)? = null,
    onImageClick: (() -> Unit)? = null
) {
    val borderColor = if (isDragging) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val backgroundColor = if (isDragging) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = backgroundColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isEditing) {
                    val icon = blockTypeIcon(block.type)
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = blockTypeLabel(block.type),
                            modifier = Modifier.size(BlockTypeIconSize),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(BlockTypeIconSpacing))
                    }
                }
                if (
                    shouldShowBlockActionMenu(
                        isEditing = isEditing,
                        hasDelete = onDelete != null,
                        hasMoveUp = onMoveUp != null,
                        hasMoveDown = onMoveDown != null
                    )
                ) {
                    BlockOverflowMenu(
                        onDelete = onDelete,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown
                    )
                }
            }
            BlockContent(
                block = block,
                isEditing = isEditing,
                onValueChange = onValueChange,
                onLanguageClick = onLanguageClick,
                modifier = Modifier.fillMaxWidth(),
                todoItems = todoItems,
                showCompletedTodos = showCompletedTodos,
                onCreateTodo = onCreateTodo,
                onUpdateTodo = onUpdateTodo,
                onCompleteTodo = onCompleteTodo,
                onToggleCompletedTodos = onToggleCompletedTodos,
                onImageClick = onImageClick
            )
        }
    }
}

fun blockTypeLabel(type: BlockType): String = when (type) {
    BlockType.TEXT -> "文本"
    BlockType.IMAGE -> "图片"
    BlockType.LINK -> "链接"
    BlockType.LATEX -> "公式"
    BlockType.CODE -> "代码"
    BlockType.DIVIDER -> "分割线"
    BlockType.TODO -> "待办"
}

fun blockTypeIcon(type: BlockType): ImageVector? = when (type) {
    BlockType.TEXT -> Icons.AutoMirrored.Filled.Article
    BlockType.IMAGE -> Icons.Default.Image
    BlockType.LINK -> Icons.Default.Link
    BlockType.LATEX -> Icons.Default.Functions
    BlockType.CODE -> Icons.Default.Code
    BlockType.DIVIDER -> null
    BlockType.TODO -> null
}

fun shouldShowBlockActionMenu(
    isEditing: Boolean,
    hasDelete: Boolean,
    hasMoveUp: Boolean,
    hasMoveDown: Boolean
): Boolean = isEditing && (hasDelete || hasMoveUp || hasMoveDown)

@Composable
private fun BlockOverflowMenu(
    onDelete: (() -> Unit)?,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "更多操作",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        if (onMoveUp != null) {
            DropdownMenuItem(
                text = { Text("上移") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = null
                    )
                },
                onClick = {
                    expanded = false
                    onMoveUp()
                }
            )
        }
        if (onMoveDown != null) {
            DropdownMenuItem(
                text = { Text("下移") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                },
                onClick = {
                    expanded = false
                    onMoveDown()
                }
            )
        }
        if (onDelete != null) {
            DropdownMenuItem(
                text = { Text("删除") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
}
