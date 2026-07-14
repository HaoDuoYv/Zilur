package com.example.zhilu.ui.note

import androidx.lifecycle.SavedStateHandle
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.KnowledgeCard
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
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteViewModelBlockOpsTest {
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
    fun `addKnowledgeCard does not crash when currentCardId not in cards`() = runTest(dispatcher) {
        // 模拟旧记录加载后 state.cards 不包含 currentCardId 的场景
        val viewModel = createViewModelWithMalformedOldNote()

        viewModel.startEditing()
        viewModel.addKnowledgeCard()

        val state = viewModel.uiState.value
        assertEquals(2, state.cards.size)
        assertTrue(state.cards.any { it.id == state.activeCardId })
    }

    private fun createViewModelWithMalformedOldNote(): NoteViewModel {
        val note = Note(
            id = 99L,
            title = "损坏的旧笔记",
            blocks = listOf(
                Block(id = 10L, type = BlockType.TEXT, content = "旧内容", sortOrder = 0)
            ),
            cards = listOf(
                KnowledgeCard(
                    id = 1L,
                    title = "卡片1",
                    blocks = listOf(
                        Block(id = 10L, type = BlockType.TEXT, content = "旧内容", sortOrder = 0)
                    )
                )
            )
        )
        val viewModel = NoteViewModel(
            noteRepository = BlockOpsTestNoteRepository(note),
            tagRepository = BlockOpsTestTagRepository(),
            reviewRepository = BlockOpsTestReviewRepository(),
            todoRepository = BlockOpsTestTodoRepository(),
            reminderRepository = BlockOpsTestReminderRepository(),
            mediaRepository = mockk(relaxed = true),
            context = mockk(relaxed = true),
            savedStateHandle = SavedStateHandle(mapOf("noteId" to note.id))
        )

        // 模拟旧记录加载后 state.cards 不包含 currentCardId 的损坏场景
        val currentCardIdField = NoteViewModel::class.java.getDeclaredField("currentCardId").apply {
            isAccessible = true
        }
        currentCardIdField.setLong(viewModel, 999L)

        val uiStateField = NoteViewModel::class.java.getDeclaredField("_uiState").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val uiState = uiStateField.get(viewModel) as MutableStateFlow<NoteUiState>
        uiState.value = uiState.value.copy(cards = emptyList())

        return viewModel
    }
}

private class BlockOpsTestNoteRepository(
    private val note: Note?
) : NoteRepository {
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

private class BlockOpsTestTagRepository : TagRepository {
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

private class BlockOpsTestReviewRepository : ReviewRepository {
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

private class BlockOpsTestReminderRepository : ReminderRepository {
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

private class BlockOpsTestTodoRepository : TodoRepository {
    override fun observeByNoteId(noteId: Long): Flow<RepositoryResult<List<TodoItem>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun addTodo(todo: TodoItem): RepositoryResult<Long> =
        RepositoryResult.Success(1L)

    override suspend fun updateTodo(todo: TodoItem): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun completeTodo(id: Long, completedAt: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)
}
