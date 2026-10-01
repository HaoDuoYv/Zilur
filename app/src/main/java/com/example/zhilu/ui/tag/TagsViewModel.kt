package com.example.zhilu.ui.tag

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TagsViewModel @Inject constructor(
    private val tagRepository: TagRepository,
    private val noteRepository: NoteRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(TagsUiState())
    val uiState: StateFlow<TagsUiState> = _uiState.asStateFlow()

    init {
        observeTags()
        observeNoteCounts()
        loadNoteCount()
    }

    fun addTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            when (val existing = tagRepository.getTagByName(trimmed)) {
                is RepositoryResult.Success -> {
                    if (existing.data == null) {
                        val color = palette[_uiState.value.tags.size % palette.size]
                        handleUnitLike(tagRepository.insertTag(Tag(name = trimmed, color = color)))
                    }
                }
                is RepositoryResult.Error -> _uiState.update { it.copy(error = existing.message) }
            }
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            handleUnitLike(tagRepository.deleteTag(tag))
            if (_uiState.value.selectedTag?.id == tag.id) {
                _uiState.update { it.copy(selectedTag = null, filteredNotes = emptyList()) }
            }
        }
    }

    fun selectTag(tag: Tag) {
        val current = _uiState.value.selectedTag
        if (current?.id == tag.id) {
            _uiState.update { it.copy(selectedTag = null, filteredNotes = emptyList(), isLoadingNotes = false) }
            return
        }
        _uiState.update { it.copy(selectedTag = tag, filteredNotes = emptyList(), isLoadingNotes = true) }
        viewModelScope.launch {
            when (val result = noteRepository.getNotesByTagId(tag.id)) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(filteredNotes = result.data, isLoadingNotes = false, error = null)
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(isLoadingNotes = false, error = result.message)
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun observeTags() {
        viewModelScope.launch {
            tagRepository.getAllTags().collect { result ->
                when (result) {
                    is RepositoryResult.Success -> _uiState.update {
                        it.copy(tags = result.data, isLoading = false, error = null)
                    }
                    is RepositoryResult.Error -> _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
            }
        }
    }

    /**
     * 统计每个标签下的笔记数，供标签索引行显示二级信息。
     *
     * 直接复用 [NoteRepository.getAllNotes] 在内存里聚合：标签数量远小于笔记数量，
     * 为此在 DAO / Repository 上加一条专用聚合查询不划算，也不值得让所有测试替身跟着改。
     */
    private fun observeNoteCounts() {
        viewModelScope.launch {
            noteRepository.getAllNotes().collect { result ->
                if (result is RepositoryResult.Success) {
                    val counts = buildMap<Long, Int> {
                        result.data.forEach { note ->
                            note.tags.forEach { tag ->
                                put(tag.id, (this[tag.id] ?: 0) + 1)
                            }
                        }
                    }
                    _uiState.update { it.copy(noteCountByTag = counts) }
                }
            }
        }
    }

    private fun loadNoteCount() {
        viewModelScope.launch {
            when (val count = noteRepository.getNoteCount()) {
                is RepositoryResult.Success -> _uiState.update { it.copy(noteCount = count.data) }
                is RepositoryResult.Error -> _uiState.update { it.copy(error = count.message) }
            }
        }
    }

    private fun handleUnitLike(result: RepositoryResult<*>) {
        if (result is RepositoryResult.Error) {
            _uiState.update { it.copy(error = result.message) }
        }
    }

    private val palette = listOf(
        0xFF6750A4.toInt(),
        0xFF0061A4.toInt(),
        0xFF006B2E.toInt(),
        0xFF946700.toInt(),
        0xFF8C1D40.toInt()
    )
}
