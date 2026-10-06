package com.example.zhilu.ui.note.knowledge

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.EmphasisTone
import com.example.zhilu.domain.model.KnowledgeCard
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.ui.component.AppCardStyle
import com.example.zhilu.ui.component.ElevationTokens
import com.example.zhilu.ui.component.GeneratingBadge
import com.example.zhilu.ui.component.rememberGeneratingPulse
import com.example.zhilu.ui.theme.AlphaTokens
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.LocalExtendedColors
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.LocalReducedMotion
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.MotionDuration
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.cardAccentColor
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.motionEnterTween
import com.example.zhilu.ui.theme.LocalThemePalette

/**
 * 知识卡片：头部（图钉 + 标题）与块列表的容器。
 * 聚焦态下边框加粗、抬升海拔，用容器本身表达「正在编辑这张卡」。
 */
@Composable
fun KnowledgeCardItem(
    card: KnowledgeCard,
    /** 卡片在笔记里的序号，用于身份色回退与序号坐标。 */
    cardIndex: Int,
    isEditing: Boolean,
    canDelete: Boolean,
    onFocus: () -> Unit,
    onTitleChange: (String) -> Unit,
    onDelete: () -> Unit,
    onSetBlockEmphasis: (Long, EmphasisTone?) -> Unit,
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
    onCiteToAi: () -> Unit = {},
    onCiteBlockToAi: (Long) -> Unit = {},
    /** 折叠 / 展开本卡（会话内状态，不落库）。 */
    onToggleCollapsed: () -> Unit = {},
    /** 长按折叠箭头：全部折叠 / 全部展开。 */
    onToggleAllCollapsed: () -> Unit = {},
    isGenerating: Boolean = false,
    generatingBlockIds: Set<Long> = emptySet(),
    modifier: Modifier = Modifier
) {
    val isFocused = card.isFocused && isEditing
    val reducedMotion = LocalReducedMotion.current
    val generatingPulse = rememberGeneratingPulse()

    val borderWidth by animateDpAsState(
        // 常态**不再描边**（§5.5）：卡片的静息身份由阴影 + 序号徽标 + 心线表达，
        // 描边只留给聚焦态，这样"聚焦"这件事才有对比度可用。
        targetValue = if (isFocused) 2.dp else 0.dp,
        animationSpec = motionEnterTween(MotionDuration.Medium, enabled = !reducedMotion),
        label = "KnowledgeCardBorderWidth"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isGenerating -> MaterialTheme.colorScheme.primary.copy(alpha = generatingPulse)
            isFocused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = motionEnterTween(MotionDuration.Medium, enabled = !reducedMotion),
        label = "KnowledgeCardBorderColor"
    )
    val shadowElevation by animateDpAsState(
        // 常态描边已经撤掉，卡片靠**阴影**分层；聚焦时再抬一档（§5.5）。
        // 注意这里不再复用 AppCardStyle.elevation：那个对象与「新增卡片」按钮共用，
        // 改它会把按钮一起改掉。
        targetValue = if (isFocused) ElevationTokens.Overlay else ElevationTokens.Raised,
        animationSpec = motionEnterTween(MotionDuration.Medium, enabled = !reducedMotion),
        label = "KnowledgeCardShadowElevation"
    )

    val shape = MaterialTheme.shapes.medium
    val darkTheme = LocalExtendedColors.current.isDark
    // 卡片身份色：用户改过就用存的，没改过按序号回退到轮转色（§5.2）。
    val accent = cardAccentColor(card.accent, cardIndex, darkTheme, LocalThemePalette.current)
    val isCollapsed = !card.isExpanded

    Surface(
        modifier = modifier
            .fillMaxWidth()
            // 折叠态的左侧 3dp 身份色竖条：折叠后卡片只剩头部，
            // 这条竖条是它唯一的颜色身份（§5.2 的三处作用范围之一）。
            .then(
                if (isCollapsed) {
                    Modifier.drawBehind {
                        drawRect(color = accent, size = Size(3.dp.toPx(), size.height))
                    }
                } else {
                    Modifier
                }
            )
            .shadow(shadowElevation, shape)
            .border(borderWidth, borderColor, shape)
            // 点卡片：**展开/收起在两种状态下都算数**，聚焦只在编辑态做。
            //
            // 早先这里是 `enabled = isEditing`，于是只读态整张卡片不响应 —— 而小节默认是收起的
            // （`CardMapper.toDomain` 给 `isExpanded = false`），用户看到的是一张张**空壳卡片**，
            // 且没有任何视觉提示告诉他去哪儿展开。AI 生成的笔记尤其明显：建完点进去全是空的。
            // 「打开笔记先看目录」是编辑态的意图，不该顺带把只读态也锁成不可看内容。
            .clickable(
                enabled = !isGenerating,
                onClick = {
                    if (isEditing) onFocus()
                    // 收起态下点卡片顺手展开：若只聚焦不展开，用户点了卡片却看不到内容，
                    // 还得再去点那个小箭头。
                    if (isCollapsed) onToggleCollapsed()
                }
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surface
    ) {
        Box {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CardHeader(
                    title = card.title,
                    onTitleChange = onTitleChange,
                    cardIndex = cardIndex,
                    accent = accent,
                    blockCount = card.blocks.count { it.parentBranchId == null },
                    summary = cardSummary(card),
                    isCollapsed = isCollapsed,
                    onToggleCollapsed = onToggleCollapsed,
                    onToggleAllCollapsed = onToggleAllCollapsed,
                    readOnly = !(isEditing && isFocused) || isGenerating,
                    showDelete = isEditing && isFocused && !isGenerating,
                    canDelete = canDelete,
                    onDelete = onDelete,
                    showCite = !isGenerating,
                    onCiteToAi = onCiteToAi
                )

                if (!isCollapsed) {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = AlphaTokens.Border)
                    )

                    CardBlockList(
                        card = card,
                        cardIndex = cardIndex,
                        isEditing = isEditing,
                        onSetBlockEmphasis = onSetBlockEmphasis,
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
                    onDragStateChange = onDragStateChange,
                    onCiteBlockToAi = onCiteBlockToAi,
                    generatingBlockIds = generatingBlockIds,
                    isCardGenerating = isGenerating
                )
                }
            }
            if (isGenerating) {
                GeneratingBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )
            }
        }
    }
}