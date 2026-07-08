package com.example.zhilu.ui.tag

import androidx.compose.runtime.Composable
import com.example.zhilu.domain.model.Tag

@Composable
fun TagChip(
    tag: Tag,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    com.example.zhilu.ui.component.TagChip(
        tag = tag,
        selected = selected,
        onClick = onClick
    )
}
