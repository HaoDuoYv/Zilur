package com.example.zhilu.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val tagRepository: TagRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        observeTags()
        observeRecentNotes()
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            search(query)
        }
    }

    fun submitSearch() {
        searchJob?.cancel()
        viewModelScope.launch { search(_uiState.value.query) }
    }

    fun useTagQuery(tagName: String) {
        onQueryChange(tagName)
        submitSearch()
    }

    fun useRecentQuery(query: String) {
        onQueryChange(query)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private suspend fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }
        _uiState.update { it.copy(isSearching = true) }
        val result = if (trimmed.startsWith("#")) {
            searchByTag(trimmed.removePrefix("#").trim())
        } else {
            noteRepository.searchNotes(trimmed)
        }
        when (result) {
            is RepositoryResult.Success -> _uiState.update {
                it.copy(
                    results = result.data,
                    recentQueries = (listOf(trimmed) + it.recentQueries).distinct().take(6),
                    isSearching = false,
                    error = null
                )
            }
            is RepositoryResult.Error -> _uiState.update {
                it.copy(isSearching = false, error = result.message)
            }
        }
    }

    private suspend fun searchByTag(tagName: String): RepositoryResult<List<Note>> {
        if (tagName.isEmpty()) return RepositoryResult.Success(emptyList())
        return when (val tagResult = tagRepository.getTagByName(tagName)) {
            is RepositoryResult.Error -> tagResult
            is RepositoryResult.Success -> {
                val tag = tagResult.data
                if (tag == null) {
                    RepositoryResult.Success(emptyList())
                } else {
                    noteRepository.getNotesByTagId(tag.id)
                }
            }
        }
    }

    private fun observeTags() {
        viewModelScope.launch {
            tagRepository.getAllTags().collect { result ->
                when (result) {
                    is RepositoryResult.Success -> _uiState.update { it.copy(tags = result.data) }
                    is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
                }
            }
        }
    }

    private fun observeRecentNotes() {
        viewModelScope.launch {
            noteRepository.getAllNotes().collect { result ->
                if (result is RepositoryResult.Success) {
                    _uiState.update {
                        it.copy(recentNotes = result.data.take(5))
                    }
                }
            }
        }
    }
}
