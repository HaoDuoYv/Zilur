package com.example.zhilu.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.Tag

@Composable
fun TagChip(
    tag: Tag,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    if (selected) {
        FilterChip(
            selected = true,
            onClick = onClick,
            label = { Text(tag.name) },
            leadingIcon = { TagDot(tag.color) }
        )
    } else {
        AssistChip(
            onClick = onClick,
            label = { Text(tag.name) },
            leadingIcon = { TagDot(tag.color) }
        )
    }
}

@Composable
private fun TagDot(color: Int) {
    Surface(
        modifier = Modifier.size(10.dp),
        shape = MaterialTheme.shapes.small,
        color = Color(color)
    ) {}
}
