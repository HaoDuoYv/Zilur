package com.example.zhilu.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.reminder.ReminderClassifier
import com.example.zhilu.domain.repository.MediaRepository
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.ReminderRepository
import com.example.zhilu.domain.repository.TagRepository
import com.example.zhilu.ui.navigation.AppIntents
import com.example.zhilu.ui.search.TagFilterItem
import com.example.zhilu.ui.theme.TagCreationPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    private val reminderRepository: ReminderRepository,
    private val appIntents: AppIntents
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val classifier = ReminderClassifier()
    private var searchJob: Job? = null

    init {
        loadNotes()
        loadStats()
        observeDueReminders()
        observeTagFilters()
        observeIntents()
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
        // 「清空」是退出整个搜索态：关键词与标签筛选一起清，否则标签还挂着，
        // 界面不会回到笔记列表（isSearchActive 仍为 true）。
        _uiState.update {
            it.copy(
                query = "",
                selectedTagIds = emptySet(),
                searchResults = emptyList(),
                isSearching = false
            )
        }
    }

    // ---- 标签筛选（标签页撤销后，这里是标签的常驻出口）----

    /** 点选 / 取消一个筛选标签（多选为 AND 语义）。 */
    fun toggleTagFilter(tagId: Long) {
        val selected = _uiState.value.selectedTagIds
        val next = if (tagId in selected) selected - tagId else selected + tagId
        _uiState.update { it.copy(selectedTagIds = next) }
        refreshSearch()
    }

    fun clearTagFilters() {
        _uiState.update { it.copy(selectedTagIds = emptySet()) }
        refreshSearch()
    }

    /** 筛选变化后立即重查（点 chip 是明确动作，不走输入防抖）。 */
    private fun refreshSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { search(_uiState.value.query) }
    }

    // ---- 标签本身的管理（新建 / 重命名 / 删除）----

    fun createTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            when (val existing = tagRepository.getTagByName(trimmed)) {
                is RepositoryResult.Success -> {
                    if (existing.data == null) {
                        val color = TagCreationPalette[_uiState.value.tagFilters.size % TagCreationPalette.size]
                        when (val insert = tagRepository.insertTag(Tag(name = trimmed, color = color))) {
                            is RepositoryResult.Success -> Unit
                            is RepositoryResult.Error -> _uiState.update { it.copy(error = insert.message) }
                        }
                    }
                }
                is RepositoryResult.Error -> _uiState.update { it.copy(error = existing.message) }
            }
        }
    }

    fun renameTag(tag: Tag, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == tag.name) return
        viewModelScope.launch {
            when (val result = tagRepository.updateTag(tag.copy(name = trimmed))) {
                is RepositoryResult.Success -> Unit
                is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            when (val result = tagRepository.deleteTag(tag)) {
                is RepositoryResult.Success -> {
                    // 删掉的标签如果正被筛选着，必须同步移除，否则筛选悬空、结果永远为空。
                    if (tag.id in _uiState.value.selectedTagIds) {
                        _uiState.update { it.copy(selectedTagIds = it.selectedTagIds - tag.id) }
                        refreshSearch()
                    }
                }
                is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    private suspend fun search(rawQuery: String) {
        val trimmed = rawQuery.trim()

        // `#标签名` 归一：能匹配到标签 → 转成一个筛选 chip 并清空输入框；匹配不到按普通关键词搜。
        // 这样"手输 #tag"与"点 chip"最终汇成同一个状态（选中标签集合），只有一套语义。
        if (trimmed.startsWith(TAG_QUERY_PREFIX)) {
            val tagName = trimmed.removePrefix(TAG_QUERY_PREFIX).trim()
            val matched = if (tagName.isEmpty()) {
                null
            } else {
                when (val result = tagRepository.getTagByName(tagName)) {
                    is RepositoryResult.Success -> result.data
                    is RepositoryResult.Error -> null
                }
            }
            if (matched != null) {
                _uiState.update {
                    it.copy(query = "", selectedTagIds = it.selectedTagIds + matched.id)
                }
            }
        }

        val keyword = _uiState.value.query.trim()
        val tagIds = _uiState.value.selectedTagIds.toList()
        if (keyword.isEmpty() && tagIds.isEmpty()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        _uiState.update { it.copy(isSearching = true) }
        when (val result = noteRepository.searchNotes(keyword, tagIds)) {
            is RepositoryResult.Success -> _uiState.update {
                it.copy(
                    searchResults = result.data,
                    recentQueries = if (keyword.isNotEmpty()) {
                        (listOf(keyword) + it.recentQueries).distinct().take(MAX_RECENT_QUERIES)
                    } else {
                        it.recentQueries
                    },
                    isSearching = false,
                    error = null
                )
            }
            is RepositoryResult.Error -> _uiState.update {
                it.copy(isSearching = false, error = result.message)
            }
        }
    }

    /** 筛选条的候选标签（按笔记数降序）：标签与其下未删除笔记数的组合。 */
    private fun observeTagFilters() {
        viewModelScope.launch {
            combine(
                tagRepository.getAllTags(),
                tagRepository.getNoteCountsByTag()
            ) { tagsResult, countsResult ->
                val tags = when (tagsResult) {
                    is RepositoryResult.Success -> tagsResult.data
                    is RepositoryResult.Error -> emptyList()
                }
                val counts = when (countsResult) {
                    is RepositoryResult.Success -> countsResult.data
                    is RepositoryResult.Error -> emptyMap()
                }
                tags.map { TagFilterItem(tag = it, noteCount = counts[it.id] ?: 0) }
                    .sortedWith(compareByDescending<TagFilterItem> { it.noteCount }.thenBy { it.tag.name })
            }.collect { items -> _uiState.update { it.copy(tagFilters = items) } }
        }
    }

    /**
     * 消费「进搜索时预置哪个标签」的一次性意图（笔记卡片上的标签胶囊 → 首页筛选）。
     *
     * 用 collect 而不是读一次：首页是顶层页，`restoreState` 会复用同一个 ViewModel。
     */
    private fun observeIntents() {
        viewModelScope.launch {
            appIntents.pendingSearchTagId.collect { pending ->
                if (pending == null) return@collect
                appIntents.consumeSearchTagId()
                // 清空关键词只留标签：用户点的是标签，预期就是"看这个标签的笔记"。
                _uiState.update {
                    it.copy(query = "", selectedTagIds = it.selectedTagIds + pending)
                }
                refreshSearch()
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