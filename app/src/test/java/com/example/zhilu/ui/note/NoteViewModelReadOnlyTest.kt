package com.example.zhilu.ui.note

import androidx.lifecycle.SavedStateHandle
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.KnowledgeCard
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderType
import com.example.zhilu.domain.model.ReminderWithContext
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.ReviewPlanWithNote
import com.example.zhilu.domain.model.ReviewRating
import com.example.zhilu.domain.model.ReviewStats
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.domain.model.TodoWithContext
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.ReminderRepository
import com.example.zhilu.domain.repository.ReviewRepository
import com.example.zhilu.domain.repository.TagRepository
import com.example.zhilu.domain.repository.TodoRepository
import io.mockk.mockk
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteViewModelReadOnlyTest {
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
    fun existingNoteLoadsInReadOnlyWithNoFocusedCards() = runTest(dispatcher) {
        val note = createNoteWithTwoCards()
        val viewModel = createViewModel(note)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("只读态下不应处于编辑模式", state.isEditing)
        assertEquals("只读态下不应有活跃卡片", null, state.activeCardId)
        assertTrue("只读态下所有卡片都应处于未聚焦状态", state.cards.all { !it.isFocused })
    }

    @Test
    fun startEditingFromReadOnlyFocusesFirstCard() = runTest(dispatcher) {
        val note = createNoteWithTwoCards()
        val viewModel = createViewModel(note)

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isEditing)

        viewModel.startEditing()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("进入编辑态后应处于编辑模式", state.isEditing)
        assertNotNull("进入编辑态后应有活跃卡片", state.activeCardId)
        assertEquals("应自动聚焦第一张卡片", state.cards.first().id, state.activeCardId)
        assertTrue("第一张卡片应处于聚焦状态", state.cards.first().isFocused)
        assertTrue("除第一张卡片外其他卡片应处于未聚焦状态", state.cards.drop(1).all { !it.isFocused })
        assertEquals(
            "活跃卡片的块应同步到内部状态",
            state.cards.first().blocks.map { it.content },
            state.blocks.map { it.content }
        )
    }

    @Test
    fun readOnlyStateExpandsAllBranchesByDefault() = runTest(dispatcher) {
        val note = Note(
            id = 12L,
            title = "分支笔记",
            cards = listOf(
                KnowledgeCard(
                    id = 1L,
                    title = "卡片1",
                    blocks = listOf(
                        Block(id = 1L, type = BlockType.BRANCH, content = "分支一", sortOrder = 0),
                        Block(id = 2L, type = BlockType.TEXT, content = "子块", sortOrder = 1, parentBranchId = 1L)
                    )
                )
            )
        )
        val viewModel = createViewModel(note)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isEditing)
        assertTrue("只读态下所有分支应默认展开", state.branchExpandedStates.values.all { it })
    }

    private fun createNoteWithTwoCards(): Note = Note(
        id = 12L,
        title = "红黑树",
        cards = listOf(
            KnowledgeCard(
                id = 1L,
                title = "卡片1",
                blocks = listOf(Block(id = 1L, type = BlockType.TEXT, content = "内容1", sortOrder = 0))
            ),
            KnowledgeCard(
                id = 2L,
                title = "卡片2",
                blocks = listOf(Block(id = 2L, type = BlockType.TEXT, content = "内容2", sortOrder = 0))
            )
        )
    )

    private fun createViewModel(note: Note): NoteViewModel = NoteViewModel(
        noteRepository = ReadOnlyFakeNoteRepository(note),
        tagRepository = ReadOnlyFakeTagRepository(),
        reviewRepository = ReadOnlyFakeReviewRepository(),
        todoRepository = ReadOnlyFakeTodoRepository(),
        reminderRepository = ReadOnlyFakeReminderRepository(),
        mediaRepository = mockk(relaxed = true),
        blockClipboardManager = mockk(relaxed = true),
        aiTaskManager = fakeAiTaskManager(),
        refManager = fakeAiRefManager(),
        context = mockk(relaxed = true),
        savedStateHandle = SavedStateHandle(mapOf("noteId" to note.id))
    )
}

private class ReadOnlyFakeNoteRepository(
    private val note: Note
) : NoteRepository {
    override fun getAllNotes(): Flow<RepositoryResult<List<Note>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override fun getFavoriteNotes(): Flow<RepositoryResult<List<Note>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override fun getDeletedNotes(): Flow<RepositoryResult<List<Note>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun getNoteById(id: Long): RepositoryResult<Note?> =
        RepositoryResult.Success(note.takeIf { it.id == id })

    override suspend fun searchNotes(keyword: String, tagIds: List<Long>): RepositoryResult<List<Note>> =
        RepositoryResult.Success(emptyList())

    override suspend fun getNotesByTagId(tagId: Long): RepositoryResult<List<Note>> =
        RepositoryResult.Success(emptyList())

    override suspend fun insertNote(note: Note): RepositoryResult<Note> =
        RepositoryResult.Success(note.copy(id = 1L))

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
}

private class ReadOnlyFakeTagRepository : TagRepository {

    override suspend fun mergeTags(sourceIds: List<Long>, targetId: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override fun getAllTags(): Flow<RepositoryResult<List<Tag>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun getTagById(id: Long): RepositoryResult<Tag?> =
        RepositoryResult.Success(null)

    override suspend fun getTagByName(name: String): RepositoryResult<Tag?> =
        RepositoryResult.Success(null)

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

    override fun getNoteCountsByTag(): Flow<RepositoryResult<Map<Long, Int>>> =
        flowOf(RepositoryResult.Success(emptyMap()))
}

private class ReadOnlyFakeReviewRepository : ReviewRepository {
    override suspend fun getPlanByNoteId(noteId: Long): RepositoryResult<ReviewPlan?> =
        RepositoryResult.Success(null)

    override fun observePlans(): Flow<RepositoryResult<List<ReviewPlanWithNote>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override fun observeStats(windowStart: Long): Flow<ReviewStats> = flowOf(ReviewStats())

    override suspend fun enablePlan(noteId: Long, now: Long): RepositoryResult<ReviewPlan> =
        RepositoryResult.Success(ReviewPlan(noteId = noteId, nextReviewAt = now))

    override suspend fun startPlan(noteId: Long, now: Long): RepositoryResult<ReviewPlan> =
        RepositoryResult.Success(ReviewPlan(noteId = noteId, nextReviewAt = now + 86_400_000L))

    override suspend fun recordReview(
        plan: ReviewPlan,
        rating: ReviewRating,
        reviewedAt: Long
    ): RepositoryResult<ReviewPlan> =
        RepositoryResult.Success(plan.copy(nextReviewAt = reviewedAt + 86_400_000L))

    override suspend fun disablePlan(noteId: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)
}

private class ReadOnlyFakeReminderRepository : ReminderRepository {
    override fun observeAll(): Flow<RepositoryResult<List<ReminderInstance>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override fun observeAllWithContext(): Flow<RepositoryResult<List<ReminderWithContext>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun getDueReminders(now: Long): RepositoryResult<List<ReminderInstance>> =
        RepositoryResult.Success(emptyList())

    override suspend fun getActiveReminder(
        type: ReminderType,
        sourceId: Long
    ): RepositoryResult<ReminderInstance?> =
        RepositoryResult.Success(null)

    override suspend fun upsertScheduled(reminder: ReminderInstance): RepositoryResult<Long> =
        RepositoryResult.Success(reminder.id)

    override suspend fun markDone(type: ReminderType, sourceId: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun markFired(reminder: ReminderInstance, firedAt: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun cancel(type: ReminderType, sourceId: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)
}

private class ReadOnlyFakeTodoRepository : TodoRepository {

    override fun observeAllWithContext(): Flow<RepositoryResult<List<TodoWithContext>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun deleteTodo(id: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override fun observeByNoteId(noteId: Long): Flow<RepositoryResult<List<TodoItem>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun addTodo(todo: TodoItem): RepositoryResult<Long> =
        RepositoryResult.Success(1L)

    override suspend fun updateTodo(todo: TodoItem): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun completeTodo(id: Long, completedAt: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun updateRemindAt(
        id: Long,
        remindAt: Long?,
        updatedAt: Long
    ): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)
}
