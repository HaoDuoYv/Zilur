package com.example.zhilu.ui.note.blocks

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.ui.note.TodoBlock

@Composable
fun BlockContent(
    block: Block,
    isEditing: Boolean,
    onValueChange: (String) -> Unit = {},
    onLanguageClick: () -> Unit = {},
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
    onAddBranchChild: (BlockType) -> Unit = {}
) {
    when (block.type) {
        BlockType.TEXT -> {
            if (isEditing) {
                TextBlockEditor(
                    value = block.content,
                    onValueChange = onValueChange,
                    blockId = block.id,
                    modifier = modifier
                )
            } else {
                ExpandableTextContent(
                    text = block.content,
                    modifier = modifier
                )
            }
        }
        BlockType.CODE -> {
            if (isEditing) {
                CodeBlockEditor(
                    value = block.content,
                    language = block.language,
                    onValueChange = onValueChange,
                    onLanguageClick = onLanguageClick,
                    modifier = modifier
                )
            } else {
                ReadOnlyCodeBlockContent(
                    value = block.content,
                    language = block.language,
                    modifier = modifier
                )
            }
        }
        BlockType.LINK -> {
            if (isEditing) {
                LinkBlockEditor(
                    value = block.content,
                    onValueChange = onValueChange,
                    modifier = modifier
                )
            } else {
                ReadOnlyLinkBlockContent(
                    value = block.content,
                    modifier = modifier
                )
            }
        }
        BlockType.LATEX -> {
            if (isEditing) {
                LatexBlockEditor(
                    value = block.content,
                    onValueChange = onValueChange,
                    blockId = block.id,
                    modifier = modifier
                )
            } else {
                ReadOnlyLatexBlockContent(value = block.content, modifier = modifier)
            }
        }
        BlockType.IMAGE -> ImageBlockView(
            value = block.content,
            onClick = onImageClick,
            modifier = modifier
        )
        BlockType.DIVIDER -> DividerBlockView(readOnly = !isEditing, modifier = modifier)
        BlockType.TODO -> TodoBlockContent(
            block = block,
            isEditing = isEditing,
            modifier = modifier,
            todoItems = todoItems,
            showCompletedTodos = showCompletedTodos,
            onCreateTodo = onCreateTodo,
            onUpdateTodo = onUpdateTodo,
            onCompleteTodo = onCompleteTodo,
            onToggleCompletedTodos = onToggleCompletedTodos
        )
        BlockType.BRANCH -> {
            if (isEditing) {
                BranchBlockEditor(
                    block = block,
                    childBlocks = branchChildBlocks,
                    isExpanded = isBranchExpanded,
                    onTitleChange = onBranchTitleChange,
                    onToggleExpanded = onToggleBranchExpanded,
                    onChildValueChange = onBranchChildValueChange,
                    onChildLanguageClick = onBranchChildLanguageClick,
                    onRemoveChild = onRemoveBranchChild,
                    onAddChild = onAddBranchChild,
                    modifier = modifier,
                    todoItems = todoItems,
                    showCompletedTodos = showCompletedTodos,
                    onCreateTodo = onCreateTodo,
                    onUpdateTodo = onUpdateTodo,
                    onCompleteTodo = onCompleteTodo,
                    onToggleCompletedTodos = onToggleCompletedTodos
                )
            } else {
                BranchBlockView(
                    block = block,
                    childBlocks = branchChildBlocks,
                    isExpanded = isBranchExpanded,
                    onToggleExpanded = onToggleBranchExpanded,
                    modifier = modifier,
                    todoItems = todoItems,
                    showCompletedTodos = showCompletedTodos,
                    onToggleCompletedTodos = onToggleCompletedTodos
                )
            }
        }
    }
}

@Composable
private fun TodoBlockContent(
    block: Block,
    isEditing: Boolean,
    modifier: Modifier,
    todoItems: List<TodoItem>?,
    showCompletedTodos: Boolean,
    onCreateTodo: (suspend (String, Long?) -> Boolean)?,
    onUpdateTodo: ((TodoItem) -> Unit)?,
    onCompleteTodo: ((Long) -> Unit)?,
    onToggleCompletedTodos: (() -> Unit)?
) {
    // `todo_items` 是 TODO 块的正式内容源，但**块自身也可能带正文** ——
    // AI 的 create_note / add_blocks 就会写 `type=todo` + content。
    // 原先只要 todoItems 非 null 就无条件渲染 TodoBlock，于是"表为空 + 块有正文"
    // 这种组合会显示成「暂无待办」，AI 写进去的待办整条看不见（真机测试发现）。
    if (todoItems != null && (todoItems.isNotEmpty() || block.content.isBlank())) {
        val supportsEditing = onCreateTodo != null &&
            onUpdateTodo != null &&
            onCompleteTodo != null

        TodoBlock(
            todoItems = todoItems,
            showCompletedTodos = showCompletedTodos,
            onCreateTodo = onCreateTodo ?: { _, _ -> false },
            onUpdateTodo = onUpdateTodo ?: {},
            onCompleteTodo = onCompleteTodo ?: {},
            onToggleCompletedTodos = onToggleCompletedTodos ?: {},
            modifier = modifier,
            readOnly = !isEditing || !supportsEditing
        )
    } else {
        Text(
            text = block.content.ifBlank { "TODO" },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ReadOnlyLinkBlockContent(
    value: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (rawTitle, rawUrl) = rememberLinkContent(value)
    val normalizedUrl = remember(rawUrl) { rawUrl.normalizeUrl() }
    val displayTitle = rawTitle.takeIf { it.isNotBlank() && it != normalizedUrl } ?: ""
    val displayLink = normalizedUrl.takeIf { it.isNotBlank() } ?: rawTitle

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (displayTitle.isNotBlank()) {
            ExpandableText(
                text = displayTitle,
                maxLines = 6,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (displayLink.isNotBlank()) {
            ExpandableText(
                text = displayLink,
                maxLines = 3,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { openLink(context, rawUrl) }
            )
        } else {
            ExpandableText(
                text = "链接",
                maxLines = 3,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun rememberLinkContent(content: String): Pair<String, String> {
    val parts = content.split("|", limit = 2)
    return when (parts.size) {
        2 -> parts[0].trim() to parts[1].trim()
        else -> "" to content.trim()
    }
}

private fun String.normalizeUrl(): String {
    val trimmed = this.trim()
    if (trimmed.isBlank()) return ""
    val duplicateHttps = "https://https://"
    val duplicateHttp = "http://http://"
    return when {
        trimmed.startsWith(duplicateHttps, ignoreCase = true) ->
            trimmed.removePrefix(duplicateHttps).let { "https://$it" }
        trimmed.startsWith(duplicateHttp, ignoreCase = true) ->
            trimmed.removePrefix(duplicateHttp).let { "http://$it" }
        trimmed.startsWith("http://", ignoreCase = true) -> trimmed
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        else -> "https://$trimmed"
    }
}

private fun isSafeUrl(url: String): Boolean =
    url.startsWith("http://", ignoreCase = true) ||
        url.startsWith("https://", ignoreCase = true)

private fun openLink(context: Context, url: String) {
    val normalized = url.normalizeUrl()
    if (!isSafeUrl(normalized)) {
        Toast.makeText(context, "链接格式不正确", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "没有权限打开链接", Toast.LENGTH_SHORT).show()
    }
}
