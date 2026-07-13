package com.example.zhilu.ui.home

import android.net.Uri
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.usecase.ImportKnowledgeUseCase

enum class ViewMode {
    LIST,
    TIMELINE
}

data class HomeUiState(
    val notes: List<Note> = emptyList(),
    val noteCount: Int = 0,
    val tagCount: Int = 0,
    val mediaCount: Int = 0,
    val viewMode: ViewMode = ViewMode.LIST,
    val isLoading: Boolean = true,
    val error: String? = null,
    val importPreview: ImportKnowledgeUseCase.Preview? = null,
    val pendingImportUri: Uri? = null
)
