package com.example.zhilu.ui.home

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderType
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.repository.MediaRepository
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.ReminderRepository
import com.example.zhilu.domain.repository.TagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelSearchTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `query debounces into a single search`() = runTest(dispatcher) {
        val noteRepository = SearchTestNoteRepository()
        val viewModel = createViewModel(noteRepository)
        advanceUntilIdle()

        viewModel.onQueryChange("二")
        viewModel.onQueryChange("二次")
        viewModel.onQueryChange("二次型")
        advanceUntilIdle()

        assertEquals("连续输入只应触发一次检索", 1, noteRepository.searchCalls.size)
        assertEquals("二次型", noteRepository.searchCalls.single())
        assertEquals("二次型", viewModel.uiState.value.query)
    }

    @Test
    fun `empty query clears results without searching`() = runTest(dispatcher) {
        val noteRepository = SearchTestNoteRepository()
        val viewModel = createViewModel(noteRepository)
        advanceUntilIdle()

        viewModel.onQueryChange("二次型")
        advanceUntilIdle()
        viewModel.clearQuery()
        advanceUntilIdle()

        assertEquals("空查询不应发起检索", 1, noteRepository.searchCalls.size)
        assertEquals(emptyList<Note>(), viewModel.uiState.value.searchResults)
        assertEquals("", viewModel.uiState.value.query)
    }

    @Test
    fun `hash prefix searches by tag instead of full text`() = runTest(dispatcher) {
        val noteRepository = SearchTestNoteRepository()
        val tagRepository = SearchTestTagRepository()
        val viewModel = createViewModel(noteRepository, tagRepository)
        advanceUntilIdle()

        viewModel.onQueryChange("#考研数学")
        advanceUntilIdle()

        assertEquals("带 # 前缀不应走全文检索", emptyList<String>(), noteRepository.searchCalls)
        assertEquals("前缀应被剥掉后用于标签检索", listOf("考研数学"), tagRepository.requestedTagNames)
        assertEquals(
            "应返回该标签下的笔记",
            listOf("标签内笔记"),
            viewModel.uiState.value.searchResults.map { it.title }
        )
    }

    @Test
    fun `recent queries are deduplicated and capped`() = runTest(dispatcher) {
        val noteRepository = SearchTestNoteRepository()
        val viewModel = createViewModel(noteRepository)
        advanceUntilIdle()

        (1..8).forEach { index ->
            viewModel.onQueryChange("关键词$index")
            advanceUntilIdle()
        }
        viewModel.onQueryChange("关键词3")
        advanceUntilIdle()

        val recent = viewModel.uiState.value.recentQueries
        assertEquals("最近搜索上限为 6 条", 6, recent.size)
        assertEquals("最新一次查询排在最前", "关键词3", recent.first())
        assertEquals("重复关键词只保留一条", recent.size, recent.distinct().size)
    }

    private fun createViewModel(
        noteRepository: NoteRepository,
        tagRepository: TagRepository = SearchTestTagRepository()
    ): HomeViewModel = HomeViewModel(
        noteRepository = noteRepository,
        tagRepository = tagRepository,
        mediaRepository = SearchTestMediaRepository(),
        reminderRepository = SearchTestReminderRepository()
    )
}

private class SearchTestNoteRepository : NoteRepository {
    val searchCalls = mutableListOf<String>()

    override fun getAllNotes(): Flow<RepositoryResult<List<Note>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override fun getFavoriteNotes(): Flow<RepositoryResult<List<Note>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override fun getDeletedNotes(): Flow<RepositoryResult<List<Note>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun getNoteById(id: Long): RepositoryResult<Note?> =
        RepositoryResult.Success(null)

    override suspend fun searchNotes(keyword: String): RepositoryResult<List<Note>> {
        searchCalls += keyword
        return RepositoryResult.Success(
            listOf(Note(id = 1L, title = "命中 $keyword", blocks = listOf(textBlock())))
        )
    }

    override suspend fun getNotesByTagId(tagId: Long): RepositoryResult<List<Note>> =
        RepositoryResult.Success(
            listOf(Note(id = 2L, title = "标签内笔记", blocks = listOf(textBlock())))
        )

    override suspend fun insertNote(note: Note): RepositoryResult<Note> =
        RepositoryResult.Success(note)

    override suspend fun updateNote(note: Note): RepositoryResult<Note> =
        RepositoryResult.Success(note)

    override suspend fun deleteNote(note: Note): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun softDeleteNote(id: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun restoreNote(id: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun clearDeletedNotes(): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun getNoteCount(): RepositoryResult<Int> =
        RepositoryResult.Success(0)

    private fun textBlock() = Block(id = 1L, type = BlockType.TEXT, content = "正文", sortOrder = 0)
}

private class SearchTestTagRepository : TagRepository {
    val requestedTagNames = mutableListOf<String>()

    override fun getAllTags(): Flow<RepositoryResult<List<Tag>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun getTagById(id: Long): RepositoryResult<Tag?> =
        RepositoryResult.Success(null)

    override suspend fun getTagByName(name: String): RepositoryResult<Tag?> {
        requestedTagNames += name
        return RepositoryResult.Success(Tag(id = 7L, name = name, color = 0xFF6B5B8A.toInt()))
    }

    override suspend fun getTagsByNoteId(noteId: Long): RepositoryResult<List<Tag>> =
        RepositoryResult.Success(emptyList())

    override suspend fun insertTag(tag: Tag): RepositoryResult<Long> =
        RepositoryResult.Success(1L)

    override suspend fun updateTag(tag: Tag): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun deleteTag(tag: Tag): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override fun getTagCount(): Flow<RepositoryResult<Int>> =
        flowOf(RepositoryResult.Success(0))
}

private class SearchTestMediaRepository : MediaRepository {
    override suspend fun getAllMedia(): RepositoryResult<List<Media>> =
        RepositoryResult.Success(emptyList())

    override suspend fun getMediaById(id: Long): RepositoryResult<Media?> =
        RepositoryResult.Success(null)

    override suspend fun insertMedia(media: Media): RepositoryResult<Long> =
        RepositoryResult.Success(1L)

    override suspend fun updateMedia(media: Media): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun deleteMedia(media: Media): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override fun getMediaCount(): Flow<RepositoryResult<Int>> =
        flowOf(RepositoryResult.Success(0))

    override suspend fun getTotalSize(): RepositoryResult<Long> =
        RepositoryResult.Success(0L)
}

private class SearchTestReminderRepository : ReminderRepository {
    override fun observeAll(): Flow<RepositoryResult<List<ReminderInstance>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun getDueReminders(now: Long): RepositoryResult<List<ReminderInstance>> =
        RepositoryResult.Success(emptyList())

    override suspend fun getActiveReminder(
        type: ReminderType,
        sourceId: Long
    ): RepositoryResult<ReminderInstance?> = RepositoryResult.Success(null)

    override suspend fun upsertScheduled(reminder: ReminderInstance): RepositoryResult<Long> =
        RepositoryResult.Success(reminder.id)

    override suspend fun markDone(type: ReminderType, sourceId: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun markFired(
        reminder: ReminderInstance,
        firedAt: Long
    ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

    override suspend fun cancel(type: ReminderType, sourceId: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)
}