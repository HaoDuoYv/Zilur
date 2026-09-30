package com.example.zhilu.ui.note.tag

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.ui.component.TagChip
import com.example.zhilu.ui.theme.AlphaTokens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPickerInline(
    availableTags: List<Tag>,
    selectedTags: List<Tag>,
    onToggle: (Tag) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }
    val selectedTagIds = remember(selectedTags) { selectedTags.map { it.id }.toSet() }
    val unselectedTags = remember(availableTags, selectedTagIds) {
        availableTags.filter { !selectedTagIds.contains(it.id) }
    }

    fun submitTag() {
        val trimmed = newTagName.trim()
        if (trimmed.isBlank()) return
        onCreate(trimmed)
        newTagName = ""
    }

    BackHandler(enabled = isExpanded && newTagName.isBlank()) {
        isExpanded = false
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures {
                    if (isExpanded && newTagName.isBlank()) {
                        isExpanded = false
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                selectedTags.forEach { tag ->
                    TagChip(
                        tag = tag,
                        selected = true,
                        onClick = { onToggle(tag) }
                    )
                }
                if (!isExpanded) {
                    AddTagChip(onClick = { isExpanded = true })
                }
            }

            if (isExpanded) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = AlphaTokens.Border)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TagInputField(
                            value = newTagName,
                            onValueChange = { newTagName = it },
                            onSubmit = { submitTag() }
                        )

                        if (unselectedTags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                unselectedTags.forEach { tag ->
                                    TagChip(
                                        tag = tag,
                                        selected = false,
                                        onClick = { onToggle(tag) }
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "暂无更多标签",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTagChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.minimumInteractiveComponentSize(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "标签",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun TagInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = AlphaTokens.Disabled)

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .defaultMinSize(minWidth = 120.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier.defaultMinSize(minWidth = 96.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.labelMedium.copy(color = textColor),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    singleLine = true,
                    cursorBrush = SolidColor(textColor),
                    decorationBox = { innerTextField ->
                        if (value.isEmpty()) {
                            Text(
                                text = "新标签",
                                style = MaterialTheme.typography.labelMedium,
                                color = placeholderColor
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }
    }
}
