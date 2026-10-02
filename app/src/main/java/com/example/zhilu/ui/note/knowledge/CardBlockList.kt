package com.example.zhilu.ui.note.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.EmphasisTone
import com.example.zhilu.domain.model.KnowledgeCard
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.ui.note.blocks.BlockGutter
import com.example.zhilu.ui.note.blocks.BlockGutterWidth
import com.example.zhilu.ui.note.blocks.BlockTypePickerSheet
import com.example.zhilu.ui.note.blocks.EditableBlock
import com.example.zhilu.ui.note.blocks.ReadOnlyBlock
import com.example.zhilu.ui.note.blocks.blockIndexLabel
import com.example.zhilu.ui.theme.AlphaTokens
import com.example.zhilu.ui.theme.LocalExtendedColors
import com.example.zhilu.ui.theme.cardAccentColor
import kotlin.math.roundToInt

private val BlockGap = 8.dp

/** 心线（卡片强调色）的透明度。太深喧宾夺主，太浅串不起结构，需真机校准。 */
private const val SpineAlpha = 0.26f

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
    /** 卡片在笔记里的序号，用于身份色回退与「01/02」这类坐标。 */
    cardIndex: Int,
    isEditing: Boolean,
    onSetBlockEmphasis: (Long, EmphasisTone?) -> Unit,
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
    val darkTheme = LocalExtendedColors.current.isDark
    val accent = cardAccentColor(card.accent, cardIndex, darkTheme)
    val gutterPx = with(LocalDensity.current) { BlockGutterWidth.toPx() }
    val spineWidthPx = with(LocalDensity.current) { 1.dp.toPx() }

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
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // 心线（§4.3）：gutter 右缘一条 1dp 竖线贯穿整张卡片的块序列。
                // 它把"一列互不相干的块"变成"一棵有根的树"——这是解决"结构不清晰"最关键的一笔，
                // 也是撤掉块级描边之后，块的从属关系唯一的连续视觉线索。
                if (topLevelBlocks.isNotEmpty()) {
                    val x = gutterPx - spineWidthPx
                    drawLine(
                        color = accent.copy(alpha = SpineAlpha),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = spineWidthPx
                    )
                }
            }
    ) {
        if (topLevelBlocks.isEmpty()) {
            Text(
                text = "点击底部工具栏添加内容块",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.Muted),
                modifier = Modifier.padding(start = BlockGutterWidth)
            )
        } else {
            topLevelBlocks.forEachIndexed { index, block ->
                val previousType = topLevelBlocks.getOrNull(index - 1)?.type
                // 垂直节奏（§4.5）：同类 4dp 紧密成组、跨类 16dp 明显分段、分割线 24dp。
                // 撤掉描边与同类发丝线之后，层级完全靠这个节奏读出来。
                val topPadding = when {
                    block.type == BlockType.DIVIDER || previousType == BlockType.DIVIDER -> 24.dp
                    previousType == block.type -> 4.dp
                    previousType != null -> 16.dp
                    else -> 0.dp
                }
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = topPadding)
                ) {
                    BlockGutter(
                        label = blockIndexLabel(index),
                        isActive = activeBlockId == block.id,
                        emphasis = block.emphasis,
                        accent = accent,
                        darkTheme = darkTheme,
                        canActivate = isEditing && card.isFocused,
                        onActivate = { onActivateBlock(block.id) },
                        // 只有"编辑态 + 卡片已聚焦 + 本块已激活"才露出标记入口：
                        // 未激活的块不该多出一个可点的圆点，那是噪声。
                        showMarker = isEditing && card.isFocused && activeBlockId == block.id,
                        onSetEmphasis = { tone -> onSetBlockEmphasis(block.id, tone) }
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .onSizeChanged { blockHeights[block.id] = it.height }
                            .offset {
                                if (isDragging) IntOffset(0, dragOffsetPx.roundToInt()) else IntOffset.Zero
                            }
                            .zIndex(if (isDragging) 1f else 0f)
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