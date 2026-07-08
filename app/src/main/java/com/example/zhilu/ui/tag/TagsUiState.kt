package com.example.zhilu.ui.tag

import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.Tag

data class TagsUiState(
    val tags: List<Tag> = emptyList(),
    val selectedTag: Tag? = null,
    val filteredNotes: List<Note> = emptyList(),
    val noteCount: Int = 0,
    val isLoading: Boolean = true,
    val isLoadingNotes: Boolean = false,
    val error: String? = null
)
