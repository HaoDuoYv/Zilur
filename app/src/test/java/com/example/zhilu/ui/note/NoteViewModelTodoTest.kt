package com.example.zhilu.ui.note

import androidx.lifecycle.SavedStateHandle
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderType
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.ReviewRating
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.ReminderRepository
import com.example.zhilu.domain.repository.ReviewRepository
import com.example.zhilu.domain.repository.TagRepository
import com.example.zhilu.domain.repository.TodoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteViewModelTodoTest {
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
    fun createTodoWithReminderSchedulesTodoReminder() = runTest(dispatcher) {
        val todoRepository = TodoTestTodoRepository()
        val reminderRepository = TodoTestReminderRepository()
        val viewModel = NoteViewModel(
            noteRepository = TodoTestNoteRepository(note = Note(id = 9L, title = "Plan")),
            tagRepository = TodoTestTagRepository(),
            reviewRepository = TodoTestReviewRepository(),
            todoRepository = todoRepository,
            reminderRepository = reminderRepository,
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 9L))
        )

        advanceUntilIdle()
        viewModel.createTodo("  draft outline  ", remindAt = 123_456L)
        advanceUntilIdle()

        assertEquals("draft outline", todoRepository.added.single().content)
        assertEquals(9L, todoRepository.added.single().noteId)
        assertEquals(123_456L, todoRepository.added.single().remindAt)
        assertEquals(1, reminderRepository.scheduled.size)
        assertEquals(ReminderType.TODO, reminderRepository.scheduled.single().type)
        assertEquals(101L, reminderRepository.scheduled.single().sourceId)
        assertEquals(9L, reminderRepository.scheduled.single().noteId)
        assertEquals(123_456L, reminderRepository.scheduled.single().dueAt)
        assertEquals("todo-101".hashCode(), reminderRepository.scheduled.single().notificationId)
    }

    @Test
    fun completeTodoMarksTodoCompleteAndReminderDone() = runTest(dispatcher) {
        val todoRepository = TodoTestTodoRepository()
        val reminderRepository = TodoTestReminderRepository()
        val viewModel = NoteViewModel(
            noteRepository = TodoTestNoteRepository(note = Note(id = 9L, title = "Plan")),
            tagRepository = TodoTestTagRepository(),
            reviewRepository = TodoTestReviewRepository(),
            todoRepository = todoRepository,
            reminderRepository = reminderRepository,
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 9L))
        )

        advanceUntilIdle()
        viewModel.completeTodo(55L)
        advanceUntilIdle()

        assertEquals(listOf(55L), todoRepository.completedIds)
        assertEquals(listOf(ReminderType.TODO to 55L), reminderRepository.done)
    }

    @Test
    fun toggleCompletedTodosFlipsState() = runTest(dispatcher) {
        val viewModel = NoteViewModel(
            noteRepository = TodoTestNoteRepository(note = Note(id = 9L, title = "Plan")),
            tagRepository = TodoTestTagRepository(),
            reviewRepository = TodoTestReviewRepository(),
            todoRepository = TodoTestTodoRepository(),
            reminderRepository = TodoTestReminderRepository(),
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 9L))
        )

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showCompletedTodos)

        viewModel.toggleCompletedTodos()

        assertTrue(viewModel.uiState.value.showCompletedTodos)
    }

    @Test
    fun updateTodoWithChangedReminderReschedulesReminder() = runTest(dispatcher) {
        val reminderRepository = TodoTestReminderRepository()
        val viewModel = NoteViewModel(
            noteRepository = TodoTestNoteRepository(note = Note(id = 9L, title = "Plan")),
            tagRepository = TodoTestTagRepository(),
            reviewRepository = TodoTestReviewRepository(),
            todoRepository = TodoTestTodoRepository(),
            reminderRepository = reminderRepository,
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 9L))
        )

        advanceUntilIdle()
        viewModel.updateTodo(TodoItem(id = 44L, noteId = 9L, content = "draft", remindAt = 777L))
        advanceUntilIdle()

        assertEquals(1, reminderRepository.scheduled.size)
        assertEquals(ReminderType.TODO, reminderRepository.scheduled.single().type)
        assertEquals(44L, reminderRepository.scheduled.single().sourceId)
        assertEquals(9L, reminderRepository.scheduled.single().noteId)
        assertEquals(777L, reminderRepository.scheduled.single().dueAt)
        assertEquals("todo-44".hashCode(), reminderRepository.scheduled.single().notificationId)
    }

    @Test
    fun updateTodoWithClearedReminderCancelsReminder() = runTest(dispatcher) {
        val reminderRepository = TodoTestReminderRepository()
        val viewModel = NoteViewModel(
            noteRepository = TodoTestNoteRepository(note = Note(id = 9L, title = "Plan")),
            tagRepository = TodoTestTagRepository(),
            reviewRepository = TodoTestReviewRepository(),
            todoRepository = TodoTestTodoRepository(),
            reminderRepository = reminderRepository,
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 9L))
        )

        advanceUntilIdle()
        viewModel.updateTodo(TodoItem(id = 44L, noteId = 9L, content = "draft", remindAt = null))
        advanceUntilIdle()

        assertEquals(listOf(ReminderType.TODO to 44L), reminderRepository.canceled)
    }

    @Test
    fun updateCompletedTodoMarksReminderDone() = runTest(dispatcher) {
        val reminderRepository = TodoTestReminderRepository()
        val viewModel = NoteViewModel(
            noteRepository = TodoTestNoteRepository(note = Note(id = 9L, title = "Plan")),
            tagRepository = TodoTestTagRepository(),
            reviewRepository = TodoTestReviewRepository(),
            todoRepository = TodoTestTodoRepository(),
            reminderRepository = reminderRepository,
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 9L))
        )

        advanceUntilIdle()
        viewModel.updateTodo(TodoItem(id = 44L, noteId = 9L, content = "draft", remindAt = 777L, completedAt = 888L))
        advanceUntilIdle()

        assertEquals(listOf(ReminderType.TODO to 44L), reminderRepository.done)
    }

    @Test
    fun newNoteWithTodoBlockCanCreateTodo() = runTest(dispatcher) {
        val noteRepository = TodoTestNoteRepository(insertedId = 42L)
        val todoRepository = TodoTestTodoRepository()
        val viewModel = NoteViewModel(
            noteRepository = noteRepository,
            tagRepository = TodoTestTagRepository(),
            reviewRepository = TodoTestReviewRepository(),
            todoRepository = todoRepository,
            reminderRepository = TodoTestReminderRepository(),
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 0L))
        )

        advanceUntilIdle()
        viewModel.addBlock(BlockType.TODO)
        advanceUntilIdle()
        assertTrue(viewModel.createTodo("first task", remindAt = null))
        advanceUntilIdle()

        assertEquals(1, noteRepository.insertedNotes.size)
        assertEquals(42L, viewModel.uiState.value.noteId)
        assertEquals(42L, todoRepository.added.single().noteId)
        assertEquals("first task", todoRepository.added.single().content)
    }

    @Test
    fun newNoteTodoCreateReturnsFalseWhenNoteInsertFails() = runTest(dispatcher) {
        val todoRepository = TodoTestTodoRepository()
        val viewModel = NoteViewModel(
            noteRepository = TodoTestNoteRepository(insertError = true),
            tagRepository = TodoTestTagRepository(),
            reviewRepository = TodoTestReviewRepository(),
            todoRepository = todoRepository,
            reminderRepository = TodoTestReminderRepository(),
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 0L))
        )

        advanceUntilIdle()
        viewModel.addBlock(BlockType.TODO)
        advanceUntilIdle()

        assertFalse(viewModel.createTodo("first task", remindAt = null))
        advanceUntilIdle()

        assertEquals(emptyList<TodoItem>(), todoRepository.added)
        assertEquals("Insert failed", viewModel.uiState.value.error)
    }

    @Test
    fun removeTodoBlockWithLiveTodosIsBlocked() = runTest(dispatcher) {
        val viewModel = NoteViewModel(
            noteRepository = TodoTestNoteRepository(note = Note(id = 9L, title = "Plan")),
            tagRepository = TodoTestTagRepository(),
            reviewRepository = TodoTestReviewRepository(),
            todoRepository = TodoTestTodoRepository(
                initialTodos = listOf(TodoItem(id = 2L, noteId = 9L, content = "live"))
            ),
            reminderRepository = TodoTestReminderRepository(),
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 9L))
        )

        advanceUntilIdle()
        viewModel.addBlock(BlockType.TODO)
        advanceUntilIdle()
        val todoBlockIndex = viewModel.uiState.value.blocks.indexOfFirst { it.type == BlockType.TODO }

        viewModel.removeBlock(todoBlockIndex)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.blocks.any { it.type == BlockType.TODO })
        assertEquals("TODO block still has linked items.", viewModel.uiState.value.error)
    }
}

private class TodoTestTodoRepository(
    initialTodos: List<TodoItem> = emptyList()
) : TodoRepository {
    private val todos = MutableStateFlow<RepositoryResult<List<TodoItem>>>(RepositoryResult.Success(initialTodos))
    val added = mutableListOf<TodoItem>()
    val completedIds = mutableListOf<Long>()
    val updated = mutableListOf<TodoItem>()

    override fun observeByNoteId(noteId: Long): Flow<RepositoryResult<List<TodoItem>>> = todos

    override suspend fun addTodo(todo: TodoItem): RepositoryResult<Long> {
        added += todo
        val id = 101L
        todos.value = RepositoryResult.Success((todos.value as RepositoryResult.Success).data + todo.copy(id = id))
        return RepositoryResult.Success(id)
    }

    override suspend fun updateTodo(todo: TodoItem): RepositoryResult<Unit> {
        updated += todo
        return RepositoryResult.Success(Unit)
    }

    override suspend fun completeTodo(id: Long, completedAt: Long): RepositoryResult<Unit> {
        completedIds += id
        return RepositoryResult.Success(Unit)
    }
}

private class TodoTestReminderRepository : ReminderRepository {
    val scheduled = mutableListOf<ReminderInstance>()
    val done = mutableListOf<Pair<ReminderType, Long>>()
    val canceled = mutableListOf<Pair<ReminderType, Long>>()

    override fun observeAll(): Flow<RepositoryResult<List<ReminderInstance>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun getDueReminders(now: Long): RepositoryResult<List<ReminderInstance>> =
        RepositoryResult.Success(emptyList())

    override suspend fun getActiveReminder(
        type: ReminderType,
        sourceId: Long
    ): RepositoryResult<ReminderInstance?> =
        RepositoryResult.Success(null)

    override suspend fun upsertScheduled(reminder: ReminderInstance): RepositoryResult<Long> {
        scheduled += reminder
        return RepositoryResult.Success(reminder.id)
    }

    override suspend fun markDone(type: ReminderType, sourceId: Long): RepositoryResult<Unit> {
        done += type to sourceId
        return RepositoryResult.Success(Unit)
    }

    override suspend fun markFired(reminder: ReminderInstance, firedAt: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun cancel(type: ReminderType, sourceId: Long): RepositoryResult<Unit> {
        canceled += type to sourceId
        return RepositoryResult.Success(Unit)
    }
}

private class TodoTestTagRepository : TagRepository {
    override fun getAllTags(): Flow<RepositoryResult<List<Tag>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun getTagById(id: Long): RepositoryResult<Tag?> =
        RepositoryResult.Success(null)

    override suspend fun getTagByName(name: String): RepositoryResult<Tag?> =
        RepositoryResult.Success(null)

    override suspend fun getTagsByNoteId(noteId: Long): RepositoryResult<List<Tag>> =
        RepositoryResult.Success(emptyList())

    override suspend fun insertTag(tag: Tag): RepositoryResult<Long> =
        RepositoryResult.Success(tag.id)

    override suspend fun updateTag(tag: Tag): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun deleteTag(tag: Tag): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override fun getTagCount(): Flow<RepositoryResult<Int>> =
        flowOf(RepositoryResult.Success(0))
}

private class TodoTestNoteRepository(
    private val note: Note? = null,
    private val insertedId: Long = 9L,
    private val insertError: Boolean = false
) : NoteRepository {
    val insertedNotes = mutableListOf<Note>()
    override fun getAllNotes(): Flow<RepositoryResult<List<Note>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override fun getFavoriteNotes(): Flow<RepositoryResult<List<Note>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override fun getDeletedNotes(): Flow<RepositoryResult<List<Note>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun getNoteById(id: Long): RepositoryResult<Note?> =
        RepositoryResult.Success(note?.takeIf { it.id == id })

    override suspend fun searchNotes(keyword: String): RepositoryResult<List<Note>> =
        RepositoryResult.Success(emptyList())

    override suspend fun getNotesByTagId(tagId: Long): RepositoryResult<List<Note>> =
        RepositoryResult.Success(emptyList())

    override suspend fun insertNote(note: Note): RepositoryResult<Long> {
        if (insertError) return RepositoryResult.Error("Insert failed")
        insertedNotes += note
        return RepositoryResult.Success(insertedId)
    }

    override suspend fun updateNote(note: Note): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

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

private class TodoTestReviewRepository : ReviewRepository {
    override suspend fun getPlanByNoteId(noteId: Long): RepositoryResult<ReviewPlan?> =
        RepositoryResult.Success(null)

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
