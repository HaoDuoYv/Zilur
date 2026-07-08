package com.example.zhilu.ui.note.tag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.ui.component.TagChip

@Composable
fun TagPickerInline(
    availableTags: List<Tag>,
    selectedTags: List<Tag>,
    onToggle: (Tag) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TagPickerContent(
        availableTags = availableTags,
        selectedTags = selectedTags,
        onToggle = onToggle,
        onCreate = onCreate,
        maxHeight = 120.dp,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagPickerContent(
    availableTags: List<Tag>,
    selectedTags: List<Tag>,
    onToggle: (Tag) -> Unit,
    onCreate: (String) -> Unit,
    maxHeight: Dp,
    modifier: Modifier = Modifier
) {
    var newTagName by remember { mutableStateOf("") }
    val selectedTagIds = remember(selectedTags) { selectedTags.map { it.id }.toSet() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun submitTag() {
        val trimmed = newTagName.trim()
        if (trimmed.isBlank()) return

        onCreate(trimmed)
        newTagName = ""
        keyboardController?.hide()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (availableTags.isEmpty()) {
            Text(
                text = "暂无标签",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                availableTags.forEach { tag ->
                    TagChip(
                        tag = tag,
                        selected = selectedTagIds.contains(tag.id),
                        onClick = { onToggle(tag) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newTagName,
                onValueChange = { newTagName = it },
                modifier = Modifier.weight(1f),
                label = { Text("新建标签") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submitTag() })
            )
            Button(
                onClick = { submitTag() },
                enabled = newTagName.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("添加")
            }
        }
    }
}
