package com.example.zhilu.ui.explore

import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.Tag

data class ExploreUiState(
    val query: String = "",
    val results: List<Note> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val recentQueries: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null
)
