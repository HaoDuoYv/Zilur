package com.example.zhilu.ui.trash

import com.example.zhilu.domain.model.Note

data class TrashUiState(
    val deletedNotes: List<Note> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)
