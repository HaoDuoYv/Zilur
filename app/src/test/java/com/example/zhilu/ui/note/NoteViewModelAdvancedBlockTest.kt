package com.example.zhilu.ui.note

import androidx.lifecycle.SavedStateHandle
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.Block
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteViewModelAdvancedBlockTest {
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
    fun addingEmptyLatexOrCodeBlockDoesNotPersistUntilContentChanges() = runTest(dispatcher) {
        val noteRepository = RecordingNoteRepository(
            note = Note(
                id = 7L,
                title = "数学",
                blocks = listOf(Block(type = BlockType.TEXT, content = "已有内容", sortOrder = 0))
            )
        )
        val viewModel = NoteViewModel(
            noteRepository = noteRepository,
            tagRepository = EmptyTagRepository(),
            reviewRepository = AdvancedBlockReviewRepository(),
            todoRepository = AdvancedBlockTodoRepository(),
            reminderRepository = AdvancedBlockReminderRepository(),
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 7L))
        )
        advanceUntilIdle()

        viewModel.addBlock(BlockType.LATEX)
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals(0, noteRepository.updatedNotes.size)
        assertTrue(viewModel.uiState.value.blocks.any { it.type == BlockType.LATEX })

        val latexIndex = viewModel.uiState.value.blocks.indexOfFirst { it.type == BlockType.LATEX }
        viewModel.onBlockContentChange(latexIndex, "\\alpha^2")
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals(1, noteRepository.updatedNotes.size)
        assertTrue(noteRepository.updatedNotes.single().blocks.any { it.type == BlockType.LATEX && it.content == "\\alpha^2" })
    }
}

private class EmptyTagRepository : TagRepository {
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
}

private class RecordingNoteRepository(
    private val note: Note
) : NoteRepository {
    val updatedNotes = mutableListOf<Note>()

    override fun getAllNotes(): Flow<RepositoryResult<List<Note>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override fun getFavoriteNotes(): Flow<RepositoryResult<List<Note>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override fun getDeletedNotes(): Flow<RepositoryResult<List<Note>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun getNoteById(id: Long): RepositoryResult<Note?> =
        RepositoryResult.Success(note.takeIf { it.id == id })

    override suspend fun searchNotes(keyword: String): RepositoryResult<List<Note>> =
        RepositoryResult.Success(emptyList())

    override suspend fun getNotesByTagId(tagId: Long): RepositoryResult<List<Note>> =
        RepositoryResult.Success(emptyList())

    override suspend fun insertNote(note: Note): RepositoryResult<Long> =
        RepositoryResult.Success(1L)

    override suspend fun updateNote(note: Note): RepositoryResult<Unit> {
        updatedNotes += note
        return RepositoryResult.Success(Unit)
    }

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

private class AdvancedBlockReviewRepository : ReviewRepository {
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

private class AdvancedBlockReminderRepository : ReminderRepository {
    override fun observeAll(): Flow<RepositoryResult<List<ReminderInstance>>> =
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

private class AdvancedBlockTodoRepository : TodoRepository {
    override fun observeByNoteId(noteId: Long): Flow<RepositoryResult<List<TodoItem>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun addTodo(todo: TodoItem): RepositoryResult<Long> =
        RepositoryResult.Success(1L)

    override suspend fun updateTodo(todo: TodoItem): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun completeTodo(id: Long, completedAt: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)
}
