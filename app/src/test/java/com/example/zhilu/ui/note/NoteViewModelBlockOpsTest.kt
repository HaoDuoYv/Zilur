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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun `addKnowledgeCard creates fallback card when currentCardId is not in state cards`() = runTest(dispatcher) {
        // 加载已存在卡片的旧笔记后，ViewModel 会生成一个全新的 currentCardId，
        // 该 ID 不会出现在 state.cards 中；此时直接添加知识卡片会触发 ensureCurrentCardExists，
        // 从而将当前编辑器中的内容保留为兜底卡片，避免旧块丢失。
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
        advanceUntilIdle()

        viewModel.addKnowledgeCard()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("应为原卡片、兜底卡片、新卡片共 3 张", 3, state.cards.size)
        assertNotNull("新建卡片后应有活跃卡片", state.activeCardId)
        assertTrue("活跃卡片 ID 必须存在于 cards 中", state.cards.any { it.id == state.activeCardId })

        val originalCardId = 1L
        val newCardId = state.activeCardId!!
        val fallbackCard = state.cards.first {
            it.id != originalCardId && it.id != newCardId
        }
        assertEquals(
            "兜底卡片应保留编辑器切换前的旧内容",
            listOf("旧内容"),
            fallbackCard.blocks.map { it.content }
        )

        val newCard = state.cards.find { it.id == newCardId }
        assertNotNull("应存在与新活跃 ID 对应的新卡片", newCard)
        assertEquals("新卡片应只包含默认空白文本块", 1, newCard!!.blocks.size)
        assertEquals(BlockType.TEXT, newCard.blocks.single().type)
        assertEquals("", newCard.blocks.single().content)

        assertEquals(
            "当前编辑状态应同步为新卡片的块",
            listOf(""),
            state.blocks.map { it.content }
        )
    }

    @Test
    fun `focusCard creates fallback card when currentCardId is not in state cards`() = runTest(dispatcher) {
        // 旧笔记加载后 currentCardId 是 ViewModel 新生成的 ID，不在 state.cards 中；
        // 直接聚焦到已有卡片时，应通过 ensureCurrentCardExists 创建兜底卡片，
        // 保留当前编辑器中的内容，避免旧块丢失。
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
        advanceUntilIdle()

        viewModel.focusCard(1L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("应为原卡片和兜底卡片共 2 张", 2, state.cards.size)
        assertEquals("活跃卡片应切换到目标卡片", 1L, state.activeCardId)

        val targetCard = state.cards.find { it.id == 1L }
        assertNotNull("目标卡片应存在", targetCard)
        assertTrue("目标卡片应处于聚焦状态", targetCard!!.isFocused)

        val fallbackCard = state.cards.first { it.id != 1L }
        assertEquals(
            "兜底卡片应保留编辑器切换前的旧内容",
            listOf("旧内容"),
            fallbackCard.blocks.map { it.content }
        )
        assertTrue("兜底卡片不应处于聚焦状态", !fallbackCard.isFocused)
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
