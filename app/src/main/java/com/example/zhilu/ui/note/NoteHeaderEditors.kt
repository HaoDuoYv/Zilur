package com.example.zhilu.ui.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.ui.component.TagChip
import com.example.zhilu.ui.note.tag.TagPickerInline

/**
 * 笔记列表的**头部**：标题 + 标签。
 *
 * 编辑态与只读态是两套写法，所以拆成两个入口（[EditModeHeader] / [ReadOnlyHeader]），
 * 它们只被 `NoteEditScreen` 的列表第 0 项调用：
 *
 * - 标题在编辑态是输入框（[TitleInput]），只读态是文本；
 * - 标签在编辑态可增删（走 [TagPickerInline]），只读态只展示已选项。
 *
 * 抽出来的原因：这段与"列表本体 + 共享可变状态"完全无关（纯参数进、纯 UI 出），
 * 留在 `NoteEditScreen` 里只会让那个已经过长的文件更难定位真正的状态机。
 */
@Composable
internal fun TitleInput(
    title: String,
    onTitleChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val textStyle = MaterialTheme.typography.headlineSmall.copy(
        color = MaterialTheme.colorScheme.onBackground
    )

    BasicTextField(
        value = title,
        onValueChange = onTitleChange,
        modifier = modifier,
        singleLine = true,
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = {}),
        decorationBox = { innerTextField ->
            if (title.isEmpty()) {
                Text(
                    text = "标题",
                    style = textStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            innerTextField()
        }
    )
}

/** 编辑态的头部：标题输入框 + 可增删的标签行。 */
@Composable
internal fun EditModeHeader(
    title: String,
    onTitleChange: (String) -> Unit,
    availableTags: List<Tag>,
    selectedTags: List<Tag>,
    onToggleTag: (Tag) -> Unit,
    onCreateTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        TitleInput(
            title = title,
            onTitleChange = onTitleChange,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        TagPickerInline(
            availableTags = availableTags,
            selectedTags = selectedTags,
            onToggle = onToggleTag,
            onCreate = onCreateTag,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 只读态的头部：标题文本 + 已选标签（不可点）。
 *
 * 新建但还没写标题时显示占位文案「新建知识」，与编辑态输入框里的「标题」占位形成对照。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReadOnlyHeader(
    title: String,
    selectedTags: List<Tag>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title.ifBlank { "新建知识" },
            style = MaterialTheme.typography.headlineSmall,
            color = if (title.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onBackground
            }
        )
        if (selectedTags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                selectedTags.forEach { tag ->
                    TagChip(tag = tag, onClick = {})
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
