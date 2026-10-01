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
     * 计数走数据层的单条 `GROUP BY` 聚合（[TagRepository.getNoteCountsByTag]）。
     * 早先的实现是复用 [NoteRepository.getAllNotes] 在内存里聚合，代价不小：
     * 每条笔记都要 hydrate 一次（卡片、区块、标签三次查询），标签页只为显示一个数字
     * 就要把整个笔记库拉进内存。笔记规模上去后这个开销是线性放大的，所以改由 SQL 承担。
     */
    private fun observeNoteCounts() {
        viewModelScope.launch {
            tagRepository.getNoteCountsByTag().collect { result ->
                when (result) {
                    is RepositoryResult.Success ->
                        _uiState.update { it.copy(noteCountByTag = result.data) }
                    // 计数只是行内的二级信息，失败时保留上一次的值即可，
                    // 不值得为它把整个标签页推进错误态。
                    is RepositoryResult.Error -> Unit
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
