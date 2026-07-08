package com.example.zhilu.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.repository.MediaRepository
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val tagRepository: TagRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadNotes()
        loadStats()
    }

    fun loadNotes() {
        viewModelScope.launch {
            noteRepository.getAllNotes().collect { result ->
                when (result) {
                    is RepositoryResult.Success -> {
                        _uiState.update {
                            it.copy(
                                notes = result.data,
                                noteCount = result.data.size,
                                isLoading = false
                            )
                        }
                    }
                    is RepositoryResult.Error -> {
                        Timber.e(result.throwable, result.message)
                        _uiState.update { it.copy(error = result.message, isLoading = false) }
                    }
                }
            }
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            tagRepository.getTagCount().collect { result ->
                if (result is RepositoryResult.Success) {
                    _uiState.update { it.copy(tagCount = result.data) }
                }
            }
        }
        viewModelScope.launch {
            mediaRepository.getMediaCount().collect { result ->
                if (result is RepositoryResult.Success) {
                    _uiState.update { it.copy(mediaCount = result.data) }
                }
            }
        }
    }

    fun toggleViewMode() {
        _uiState.update {
            it.copy(viewMode = if (it.viewMode == ViewMode.LIST) ViewMode.TIMELINE else ViewMode.LIST)
        }
    }

    fun toggleFavorite(note: Note) {
        viewModelScope.launch {
            noteRepository.updateNote(note.copy(isFavorite = !note.isFavorite))
        }
    }

    fun softDeleteNote(noteId: Long) {
        viewModelScope.launch {
            noteRepository.softDeleteNote(noteId)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
