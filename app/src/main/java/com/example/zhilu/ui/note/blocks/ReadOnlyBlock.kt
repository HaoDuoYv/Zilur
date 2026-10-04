package com.example.zhilu.ui.note.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.AnnotatedString
import com.example.zhilu.domain.markup.InlineMarkup
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.ui.component.GeneratingBadge
import com.example.zhilu.ui.component.rememberGeneratingPulse
import com.example.zhilu.ui.theme.AlphaTokens

@Composable
fun ReadOnlyBlock(
    block: Block,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    onLanguageClick: () -> Unit = {},
    todoItems: List<TodoItem>? = null,
    showCompletedTodos: Boolean = false,
    onCreateTodo: (suspend (String, Long?) -> Boolean)? = null,
    onUpdateTodo: ((TodoItem) -> Unit)? = null,
    onCompleteTodo: ((Long) -> Unit)? = null,
    onToggleCompletedTodos: (() -> Unit)? = null,
    onImageClick: (() -> Unit)? = null,
    branchChildBlocks: List<Block> = emptyList(),
    isBranchExpanded: Boolean = false,
    onToggleBranchExpanded: () -> Unit = {},
    onToggleCompletedTodosInBranch: (() -> Unit)? = null,
    onCiteToAi: () -> Unit = {},
    isGenerating: Boolean = false,
    showBadge: Boolean = false
) {
    val generatingPulse = rememberGeneratingPulse()
    val generatingBorder = MaterialTheme.colorScheme.primary.copy(alpha = generatingPulse)

    // 长按不再挂在最外层：正文块自己带 SelectableBlockText（可选文本 + 块级菜单），
    // 外层若再认领长按，选区就永远起不来（父级先于子级收到指针事件）。见 SelectableBlockText 的说明。
    Box(modifier = modifier) {
        BlockCard(
            block = block,
            isEditing = false,
            onLanguageClick = onLanguageClick,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isGenerating) {
                        Modifier.border(1.dp, generatingBorder, MaterialTheme.shapes.medium)
                    } else {
                        Modifier
                    }
                ),
            todoItems = todoItems,
            showCompletedTodos = showCompletedTodos,
            onCreateTodo = onCreateTodo,
            onUpdateTodo = onUpdateTodo,
            onCompleteTodo = onCompleteTodo,
            onToggleCompletedTodos = onToggleCompletedTodos,
            onImageClick = onImageClick,
            onCiteBlockToAi = onCiteToAi,
            branchChildBlocks = branchChildBlocks,
            isBranchExpanded = isBranchExpanded,
            onToggleBranchExpanded = onToggleBranchExpanded
        )
        if (isGenerating && showBadge) {
            GeneratingBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            )
        }
    }
}

fun readOnlyBlockCopyMenuLabel(): String = "复制此块"

/**
 * 复制到系统剪贴板的纯文本。
 *
 * 正文与分支标题要**先剥离行内语法**：这是给"人读、往别处粘"的出口，
 * 粘到聊天/邮件里带一串 `{{k:` 没有意义。块剪贴板（应用内粘贴）走的是另一条路，
 * 那条保留原文，因为标记要跟着块一起搬走。
 */
fun readOnlyBlockClipboardText(block: Block): AnnotatedString {
    val text = when (block.type) {
        com.example.zhilu.domain.model.BlockType.IMAGE -> "[图片]"
        com.example.zhilu.domain.model.BlockType.DIVIDER -> "----"
        com.example.zhilu.domain.model.BlockType.BRANCH ->
            InlineMarkup.stripMarkup(block.content).ifBlank { "[分支]" }
        com.example.zhilu.domain.model.BlockType.LATEX ->
            block.content.ifBlank { "[公式]" }
        com.example.zhilu.domain.model.BlockType.TEXT ->
            InlineMarkup.stripMarkup(block.content)
        else -> block.content
    }
    return AnnotatedString(text)
}
