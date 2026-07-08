package com.example.zhilu.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    init {
        observeTrash()
    }

    fun restore(noteId: Long) {
        viewModelScope.launch {
            handle(noteRepository.restoreNote(noteId))
        }
    }

    fun deleteForever(note: Note) {
        viewModelScope.launch {
            handle(noteRepository.deleteNote(note))
        }
    }

    fun clearTrash() {
        viewModelScope.launch {
            handle(noteRepository.clearDeletedNotes())
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun observeTrash() {
        viewModelScope.launch {
            noteRepository.getDeletedNotes().collect { result ->
                when (result) {
                    is RepositoryResult.Success -> _uiState.update {
                        it.copy(deletedNotes = result.data, isLoading = false, error = null)
                    }
                    is RepositoryResult.Error -> _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
            }
        }
    }

    private fun handle(result: RepositoryResult<Unit>) {
        if (result is RepositoryResult.Error) {
            _uiState.update { it.copy(error = result.message) }
        }
    }
}
