package com.example.zhilu.ui.note.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.KnowledgeCard
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.ui.note.blocks.BlockTypePickerSheet
import com.example.zhilu.ui.note.blocks.EditableBlock
import com.example.zhilu.ui.note.blocks.ReadOnlyBlock
import com.example.zhilu.ui.theme.AlphaTokens
import kotlin.math.roundToInt

private val BlockGap = 8.dp

/**
 * 知识卡片内的顶层块列表。
 *
 * 编辑态下激活块会露出拖拽把手：按住拖动即跟随手指位移，累计位移超过相邻块高度的一半时
 * 与相邻块交换位置（落到 [onReorderBlock]），松手后由 [onDragStateChange] 触发一次落盘。
 * 分支块带着子块整体移动，子块不参与顶层排序。
 */
@Composable
fun CardBlockList(
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
    activeBlockId: Long?,
    onActivateBlock: (Long) -> Unit,
    onReorderBlock: (Long, Long) -> Unit,
    onDragStateChange: (Boolean) -> Unit,
    onCiteBlockToAi: (Long) -> Unit = {},
    generatingBlockIds: Set<Long> = emptySet(),
    isCardGenerating: Boolean = false,
    modifier: Modifier = Modifier
) {
    val topLevelBlocks = card.blocks.filter { it.parentBranchId == null }
    var pendingInsertIndex by remember { mutableIntStateOf(-1) }
    var draggingBlockId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val blockHeights = remember { mutableStateMapOf<Long, Int>() }
    val blockGapPx = with(LocalDensity.current) { BlockGap.toPx() }

    fun onBlockDrag(block: Block, deltaY: Float) {
        dragOffsetPx += deltaY
        var index = topLevelBlocks.indexOfFirst { it.id == block.id }
        if (index < 0) return

        while (index < topLevelBlocks.lastIndex) {
            val next = topLevelBlocks[index + 1]
            val step = (blockHeights[next.id] ?: 0) + blockGapPx
            if (step <= 0f || dragOffsetPx <= step / 2f) break
            onReorderBlock(block.id, next.id)
            dragOffsetPx -= step
            index++
        }
        while (index > 0) {
            val previous = topLevelBlocks[index - 1]
            val step = (blockHeights[previous.id] ?: 0) + blockGapPx
            if (step <= 0f || dragOffsetPx >= -step / 2f) break
            onReorderBlock(block.id, previous.id)
            dragOffsetPx += step
            index--
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BlockGap)
    ) {
        if (topLevelBlocks.isEmpty()) {
            Text(
                text = "点击底部工具栏添加内容块",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.Muted)
            )
        } else {
            topLevelBlocks.forEachIndexed { index, block ->
                val previousType = topLevelBlocks.getOrNull(index - 1)?.type
                val topPadding = when {
                    block.type == BlockType.DIVIDER || previousType == BlockType.DIVIDER -> 24.dp
                    previousType == block.type -> 8.dp
                    previousType != null -> 16.dp
                    else -> 0.dp
                }
                val showTopDivider = previousType != null &&
                    previousType == block.type &&
                    block.type != BlockType.DIVIDER &&
                    block.type != BlockType.IMAGE &&
                    block.type != BlockType.LATEX
                val childBlocks = card.blocks.filter { it.parentBranchId == block.id }
                val isBranchExpanded = if (isEditing) {
                    branchExpandedStates[block.id] ?: true
                } else {
                    true
                }
                val blockIndexInAll = card.blocks.indexOfFirst { it.id == block.id }
                val childCount = card.blocks.count { it.parentBranchId == block.id }
                val isDragging = draggingBlockId == block.id
                val isBlockGenerating = isCardGenerating || block.id in generatingBlockIds

                Box(
                    modifier = Modifier
                        .onSizeChanged { blockHeights[block.id] = it.height }
                        .offset {
                            if (isDragging) IntOffset(0, dragOffsetPx.roundToInt()) else IntOffset.Zero
                        }
                        .zIndex(if (isDragging) 1f else 0f)
                        .padding(top = topPadding)
                ) {
                    if (isEditing && card.isFocused) {
                        EditableBlock(
                            index = index,
                            block = block,
                            total = topLevelBlocks.size,
                            onValueChange = { onBlockContentChange(block.id, it) },
                            onLanguageClick = { onBlockLanguageClick(block.id) },
                            onRemove = { onRemoveBlock(block.id) },
                            onMoveUp = { onMoveBlockUp(block.id) },
                            onMoveDown = { onMoveBlockDown(block.id) },
                            onDrag = { deltaY -> onBlockDrag(block, deltaY) },
                            onDragStart = {
                                draggingBlockId = block.id
                                dragOffsetPx = 0f
                                onDragStateChange(true)
                            },
                            onDragEnd = {
                                draggingBlockId = null
                                dragOffsetPx = 0f
                                onDragStateChange(false)
                            },
                            isDragging = isDragging,
                            onInsertAbove = { pendingInsertIndex = blockIndexInAll },
                            onInsertBelow = { pendingInsertIndex = blockIndexInAll + 1 + childCount },
                            onCopy = { onCopyBlock(block.id) },
                            onImageClick = if (block.type == BlockType.IMAGE) {
                                { onImageClick(block) }
                            } else {
                                null
                            },
                            showTopDivider = showTopDivider,
                            isActive = activeBlockId == block.id,
                            onActivate = { onActivateBlock(block.id) },
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
                            },
                            onCiteToAi = { onCiteBlockToAi(block.id) },
                            isGenerating = isBlockGenerating
                        )
                    } else {
                        ReadOnlyBlock(
                            block = block,
                            onCopy = {},
                            showTopDivider = showTopDivider,
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
                            onToggleBranchExpanded = { onToggleBranchExpanded(block.id) },
                            onCiteToAi = { onCiteBlockToAi(block.id) },
                            isGenerating = isBlockGenerating,
                            showBadge = block.id in generatingBlockIds && !isCardGenerating
                        )
                    }
                }
            }
        }
    }

    if (pendingInsertIndex >= 0) {
        BlockTypePickerSheet(
            onDismiss = { pendingInsertIndex = -1 },
            onSelect = { type ->
                onInsertBlockAt(pendingInsertIndex, type)
                pendingInsertIndex = -1
            }
        )
    }
}