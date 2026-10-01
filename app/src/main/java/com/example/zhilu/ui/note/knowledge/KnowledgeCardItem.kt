package com.example.zhilu.ui.note.knowledge

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.KnowledgeCard
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.ui.component.AppCardStyle
import com.example.zhilu.ui.component.ElevationTokens
import com.example.zhilu.ui.theme.AlphaTokens
import com.example.zhilu.ui.theme.LocalReducedMotion
import com.example.zhilu.ui.theme.MotionDuration
import com.example.zhilu.ui.theme.motionEnterTween

/**
 * 知识卡片：头部（图钉 + 标题）与块列表的容器。
 * 聚焦态下边框加粗、抬升海拔，用容器本身表达「正在编辑这张卡」。
 */
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
    activeBlockId: Long? = null,
    onActivateBlock: (Long) -> Unit = {},
    onReorderBlock: (Long, Long) -> Unit = { _, _ -> },
    onDragStateChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isFocused = card.isFocused && isEditing
    val reducedMotion = LocalReducedMotion.current

    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) 2.dp else AppCardStyle.borderWidth,
        animationSpec = motionEnterTween(MotionDuration.Medium, enabled = !reducedMotion),
        label = "KnowledgeCardBorderWidth"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = motionEnterTween(MotionDuration.Medium, enabled = !reducedMotion),
        label = "KnowledgeCardBorderColor"
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isFocused) ElevationTokens.Overlay else AppCardStyle.elevation,
        animationSpec = motionEnterTween(MotionDuration.Medium, enabled = !reducedMotion),
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
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CardHeader(
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
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = AlphaTokens.Border)
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
                onToggleCompletedTodos = onToggleCompletedTodos,
                activeBlockId = activeBlockId,
                onActivateBlock = onActivateBlock,
                onReorderBlock = onReorderBlock,
                onDragStateChange = onDragStateChange
            )
        }
    }
}