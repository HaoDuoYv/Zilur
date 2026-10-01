package com.example.zhilu.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.reminder.ReminderClassifier
import com.example.zhilu.domain.repository.MediaRepository
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.ReminderRepository
import com.example.zhilu.domain.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 笔记页状态：笔记列表、列表 / 时间线模式、页内搜索与到期提醒计数。
 *
 * 搜索原先住在探索页，探索页移除后并入此处，避免同一能力存在两套实现。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val tagRepository: TagRepository,
    private val mediaRepository: MediaRepository,
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val classifier = ReminderClassifier()
    private var searchJob: Job? = null

    init {
        loadNotes()
        loadStats()
        observeDueReminders()
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

    /**
     * 显式设置收藏态。
     *
     * 收藏与「撤销收藏」走同一入口：撤销不是「再取反一次」，而是把状态写回原值，
     * 这样即使列表在 Snackbar 显示期间刷新过，结果也不会漂移。
     *
     * 注意不要在这里加「值相同就跳过」的守卫——传进来的 [note] 是列表渲染时的快照，
     * 它的 `isFavorite` 是**切换前**的旧值，拿它做比较会让撤销被静默跳过。
     */
    fun setFavorite(note: Note, favorite: Boolean) {
        viewModelScope.launch {
            noteRepository.updateNote(note.copy(isFavorite = favorite))
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

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            search(query)
        }
    }

    fun submitSearch() {
        searchJob?.cancel()
        viewModelScope.launch { search(_uiState.value.query) }
    }

    fun useRecentQuery(query: String) {
        onQueryChange(query)
    }

    fun clearQuery() {
        searchJob?.cancel()
        _uiState.update { it.copy(query = "", searchResults = emptyList(), isSearching = false) }
    }

    private suspend fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        _uiState.update { it.copy(isSearching = true) }
        val result = if (trimmed.startsWith(TAG_QUERY_PREFIX)) {
            searchByTag(trimmed.removePrefix(TAG_QUERY_PREFIX).trim())
        } else {
            noteRepository.searchNotes(trimmed)
        }
        when (result) {
            is RepositoryResult.Success -> _uiState.update {
                it.copy(
                    searchResults = result.data,
                    recentQueries = (listOf(trimmed) + it.recentQueries)
                        .distinct()
                        .take(MAX_RECENT_QUERIES),
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

    private fun observeDueReminders() {
        viewModelScope.launch {
            reminderRepository.observeAll().collect { result ->
                if (result is RepositoryResult.Success) {
                    val bucket = classifier.classify(
                        reminders = result.data,
                        now = System.currentTimeMillis(),
                        startOfToday = startOfToday()
                    )
                    _uiState.update {
                        it.copy(dueReminderCount = bucket.overdue.size + bucket.today.size)
                    }
                }
            }
        }
    }

    companion object {
        const val MAX_RECENT_QUERIES = 6
        const val SEARCH_DEBOUNCE_MILLIS = 300L

        /** 以 `#` 开头时按标签检索，而非全文匹配。 */
        const val TAG_QUERY_PREFIX = "#"

        private fun startOfToday(): Long = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}