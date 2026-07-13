package com.example.zhilu.ui.note.blocks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.ui.component.ImageViewer

private val BranchBackgroundColor = Color(0xFFF1F5F9)
private val BranchCornerRadius = 8.dp
private val BranchContentIndent = 24.dp
private val BranchChildSpacing = 6.dp
private val BranchHeaderSpacing = 8.dp
private val BranchAddButtonSpacing = 12.dp

@Composable
fun BranchBlockEditor(
    block: Block,
    childBlocks: List<Block>,
    isExpanded: Boolean,
    onTitleChange: (String) -> Unit,
    onToggleExpanded: () -> Unit,
    onChildValueChange: (Long, String) -> Unit,
    onChildLanguageClick: (Long) -> Unit,
    onRemoveChild: (Long) -> Unit,
    onAddChild: (BlockType) -> Unit,
    modifier: Modifier = Modifier,
    todoItems: List<TodoItem>? = null,
    showCompletedTodos: Boolean = false,
    onCreateTodo: (suspend (String, Long?) -> Boolean)? = null,
    onUpdateTodo: ((TodoItem) -> Unit)? = null,
    onCompleteTodo: ((Long) -> Unit)? = null,
    onToggleCompletedTodos: (() -> Unit)? = null
) {
    var viewingImageUri by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(BranchCornerRadius),
        color = BranchBackgroundColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(BranchHeaderSpacing)
        ) {
            BranchHeader(
                title = block.content,
                isExpanded = isExpanded,
                isEditing = true,
                onTitleChange = onTitleChange,
                onToggleExpanded = onToggleExpanded
            )
            if (isExpanded) {
                Spacer(modifier = Modifier.height(BranchHeaderSpacing))
                Column(
                    modifier = Modifier.padding(start = BranchContentIndent),
                    verticalArrangement = Arrangement.spacedBy(BranchChildSpacing)
                ) {
                    childBlocks.forEach { child ->
                        BranchChildCard(
                            child = child,
                            isEditing = true,
                            onValueChange = { onChildValueChange(child.id, it) },
                            onLanguageClick = { onChildLanguageClick(child.id) },
                            onDelete = { onRemoveChild(child.id) },
                            todoItems = todoItems,
                            showCompletedTodos = showCompletedTodos,
                            onCreateTodo = onCreateTodo,
                            onUpdateTodo = onUpdateTodo,
                            onCompleteTodo = onCompleteTodo,
                            onToggleCompletedTodos = onToggleCompletedTodos,
                            onImageClick = { viewingImageUri = ImageBlockContent.displayUri(child.content) }
                        )
                    }
                    BranchAddChildToolbar(
                        onAddText = { onAddChild(BlockType.TEXT) },
                        onAddImage = { onAddChild(BlockType.IMAGE) },
                        onAddLatex = { onAddChild(BlockType.LATEX) }
                    )
                }
            }
        }
    }

    viewingImageUri?.let { uri ->
        ImageViewer(
            imageUri = uri,
            onClose = { viewingImageUri = null }
        )
    }
}

@Composable
fun BranchBlockView(
    block: Block,
    childBlocks: List<Block>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    todoItems: List<TodoItem>? = null,
    showCompletedTodos: Boolean = false,
    onToggleCompletedTodos: (() -> Unit)? = null
) {
    var viewingImageUri by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(BranchCornerRadius),
        color = BranchBackgroundColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(BranchHeaderSpacing)
        ) {
            BranchHeader(
                title = block.content,
                isExpanded = isExpanded,
                isEditing = false,
                onTitleChange = {},
                onToggleExpanded = onToggleExpanded
            )
            if (isExpanded) {
                Spacer(modifier = Modifier.height(BranchHeaderSpacing))
                Column(
                    modifier = Modifier.padding(start = BranchContentIndent),
                    verticalArrangement = Arrangement.spacedBy(BranchChildSpacing)
                ) {
                    childBlocks.forEach { child ->
                        BlockContent(
                            block = child,
                            isEditing = false,
                            modifier = Modifier.fillMaxWidth(),
                            todoItems = todoItems,
                            showCompletedTodos = showCompletedTodos,
                            onToggleCompletedTodos = onToggleCompletedTodos,
                            onImageClick = { viewingImageUri = ImageBlockContent.displayUri(child.content) }
                        )
                    }
                }
            }
        }
    }

    viewingImageUri?.let { uri ->
        ImageViewer(
            imageUri = uri,
            onClose = { viewingImageUri = null }
        )
    }
}

@Composable
private fun BranchHeader(
    title: String,
    isExpanded: Boolean,
    isEditing: Boolean,
    onTitleChange: (String) -> Unit,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        label = "branch_arrow_rotation"
    )
    val textStyle = MaterialTheme.typography.bodyLarge.merge(
        TextStyle(color = MaterialTheme.colorScheme.onSurface)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggleExpanded() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BranchHeaderSpacing)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (isExpanded) "收起" else "展开",
            modifier = Modifier
                .size(20.dp)
                .rotate(rotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (isEditing) {
            BasicTextField(
                value = title,
                onValueChange = onTitleChange,
                modifier = Modifier.weight(1f),
                textStyle = textStyle,
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (title.isEmpty()) {
                            Text(
                                text = "分支标题",
                                style = textStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )
        } else {
            Text(
                text = title.ifBlank { "分支" },
                style = textStyle,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BranchChildCard(
    child: Block,
    isEditing: Boolean,
    onValueChange: (String) -> Unit,
    onLanguageClick: () -> Unit,
    onDelete: () -> Unit,
    onImageClick: () -> Unit,
    modifier: Modifier = Modifier,
    todoItems: List<TodoItem>? = null,
    showCompletedTodos: Boolean = false,
    onCreateTodo: (suspend (String, Long?) -> Boolean)? = null,
    onUpdateTodo: ((TodoItem) -> Unit)? = null,
    onCompleteTodo: ((Long) -> Unit)? = null,
    onToggleCompletedTodos: (() -> Unit)? = null
) {
    BlockCard(
        block = child,
        isEditing = isEditing,
        onValueChange = onValueChange,
        onLanguageClick = onLanguageClick,
        onDelete = onDelete,
        modifier = modifier.fillMaxWidth(),
        todoItems = todoItems,
        showCompletedTodos = showCompletedTodos,
        onCreateTodo = onCreateTodo,
        onUpdateTodo = onUpdateTodo,
        onCompleteTodo = onCompleteTodo,
        onToggleCompletedTodos = onToggleCompletedTodos,
        onImageClick = onImageClick
    )
}

@Composable
private fun BranchAddChildToolbar(
    onAddText: () -> Unit,
    onAddImage: () -> Unit,
    onAddLatex: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BranchAddButtonSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onAddText,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Article,
                contentDescription = "添加文本",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onAddImage,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = "添加图片",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onAddLatex,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Functions,
                contentDescription = "添加公式",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
