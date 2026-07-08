package com.example.zhilu.ui.note.bottomsheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.ui.note.tag.TagPickerContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagPickerBottomSheet(
    availableTags: List<Tag>,
    selectedTags: List<Tag>,
    onToggle: (Tag) -> Unit,
    onCreate: (String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "选择标签",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            TagPickerContent(
                availableTags = availableTags,
                selectedTags = selectedTags,
                onToggle = onToggle,
                onCreate = onCreate,
                maxHeight = 360.dp
            )
        }
    }
}
