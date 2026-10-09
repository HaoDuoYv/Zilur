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
import com.example.zhilu.domain.usecase.clipboard.BlockClipboardData
import com.example.zhilu.domain.usecase.clipboard.BlockClipboardManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
        val viewModel = createViewModel(note)
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
        val viewModel = createViewModel(note)
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

    @Test
    fun `insertBlockAt inserts at correct position`() = runTest(dispatcher) {
        val note = Note(
            id = 7L,
            title = "Ordered",
            blocks = listOf(
                Block(id = 1L, type = BlockType.TEXT, content = "A", sortOrder = 0),
                Block(id = 2L, type = BlockType.TEXT, content = "B", sortOrder = 1)
            )
        )
        val viewModel = createViewModel(note)
        advanceUntilIdle()

        viewModel.insertBlockAt(1, BlockType.CODE)
        advanceUntilIdle()

        val blocks = viewModel.uiState.value.blocks
        assertEquals("应在索引 1 处插入新块", 3, blocks.size)
        assertEquals("A", blocks[0].content)
        assertEquals(BlockType.CODE, blocks[1].type)
        assertEquals("B", blocks[2].content)
        assertEquals("sortOrder 应按新顺序重算", listOf(0, 1, 2), blocks.map { it.sortOrder })
    }

    @Test
    fun `copyBlock writes block to clipboard`() = runTest(dispatcher) {
        val note = Note(
            id = 7L,
            title = "Test",
            blocks = listOf(Block(id = 10L, type = BlockType.TEXT, content = "copy me", sortOrder = 0))
        )
        val clipboardManager = mockk<BlockClipboardManager>(relaxed = true)
        val viewModel = createViewModel(note, clipboardManager)
        advanceUntilIdle()

        viewModel.copyBlock(10L)

        val slot = slot<BlockClipboardData>()
        verify { clipboardManager.copyBlock(capture(slot)) }
        assertEquals(BlockType.TEXT, slot.captured.type)
        assertEquals("copy me", slot.captured.content)
        assertEquals(emptyList<BlockClipboardData>(), slot.captured.children)
    }

    @Test
    fun `pasteBlock generates new ids and preserves content`() = runTest(dispatcher) {
        val note = Note(
            id = 7L,
            title = "Paste",
            blocks = listOf(Block(id = 1L, type = BlockType.TEXT, content = "A", sortOrder = 0))
        )
        val clipboardManager = mockk<BlockClipboardManager>(relaxed = true)
        every { clipboardManager.readBlock() } returns BlockClipboardData(
            block = Block(type = BlockType.TEXT, content = "copied"),
            children = emptyList()
        )
        val viewModel = createViewModel(note, clipboardManager)
        advanceUntilIdle()

        viewModel.pasteBlock(1)
        advanceUntilIdle()

        val blocks = viewModel.uiState.value.blocks
        assertEquals("应插入剪贴板块", 2, blocks.size)
        assertEquals("A", blocks[0].content)
        assertEquals("copied", blocks[1].content)
        assertTrue("粘贴后的块应生成新 ID", blocks[1].id != 0L)
        assertEquals("sortOrder 应按新顺序重算", listOf(0, 1), blocks.map { it.sortOrder })
    }

    @Test
    fun `pasteBlock recreates branch children with new parent ids`() = runTest(dispatcher) {
        val note = Note(
            id = 7L,
            title = "Paste branch",
            blocks = listOf(Block(id = 1L, type = BlockType.TEXT, content = "A", sortOrder = 0))
        )
        val clipboardManager = mockk<BlockClipboardManager>(relaxed = true)
        every { clipboardManager.readBlock() } returns BlockClipboardData(
            block = Block(type = BlockType.BRANCH, content = "branch"),
            children = listOf(
                BlockClipboardData(block = Block(type = BlockType.TEXT, content = "child"))
            )
        )
        val viewModel = createViewModel(note, clipboardManager)
        advanceUntilIdle()

        viewModel.pasteBlock(1)
        advanceUntilIdle()

        val blocks = viewModel.uiState.value.blocks
        assertEquals("应粘贴分支块及其子块", 3, blocks.size)
        val pastedBranch = blocks.first { it.type == BlockType.BRANCH }
        val pastedChild = blocks.first { it.type == BlockType.TEXT && it.content == "child" }
        assertTrue("分支块应生成新 ID", pastedBranch.id != 0L)
        assertTrue("子块应生成与分支不同的新 ID", pastedChild.id != 0L && pastedChild.id != pastedBranch.id)
        assertEquals("子块的 parentBranchId 应指向新分支 ID", pastedBranch.id, pastedChild.parentBranchId)
        assertEquals(listOf("A", "branch", "child"), blocks.map { it.content })
    }

    @Test
    fun `pasteBlock preserves order when pasting multiple blocks at non end index`() = runTest(dispatcher) {
        val note = Note(
            id = 7L,
            title = "Paste multiple",
            blocks = listOf(
                Block(id = 1L, type = BlockType.TEXT, content = "A", sortOrder = 0),
                Block(id = 2L, type = BlockType.TEXT, content = "B", sortOrder = 1)
            )
        )
        val clipboardManager = mockk<BlockClipboardManager>(relaxed = true)
        every { clipboardManager.readBlock() } returns BlockClipboardData(
            block = Block(type = BlockType.TEXT, content = "X"),
            children = listOf(
                BlockClipboardData(block = Block(type = BlockType.TEXT, content = "Y")),
                BlockClipboardData(block = Block(type = BlockType.TEXT, content = "Z"))
            )
        )
        val viewModel = createViewModel(note, clipboardManager)
        advanceUntilIdle()

        viewModel.pasteBlock(1)
        advanceUntilIdle()

        val blocks = viewModel.uiState.value.blocks
        assertEquals("应粘贴顶层块及其两个子块", 5, blocks.size)
        assertEquals("粘贴到中间位置时应保持顺序", listOf("A", "X", "Y", "Z", "B"), blocks.map { it.content })
        assertEquals("sortOrder 应按新顺序重算", listOf(0, 1, 2, 3, 4), blocks.map { it.sortOrder })
    }

    @Test
    fun `moveBlock moves block down after target`() = runTest(dispatcher) {
        val viewModel = createViewModel(threeTextBlocks())
        advanceUntilIdle()

        viewModel.moveBlock(1L, 2L)
        advanceUntilIdle()

        val blocks = viewModel.uiState.value.blocks
        assertEquals("拖动向下时应落到目标块之后", listOf("B", "A", "C"), blocks.map { it.content })
        assertEquals("sortOrder 应按新顺序重算", listOf(0, 1, 2), blocks.map { it.sortOrder })
    }

    @Test
    fun `moveBlock moves block up before target`() = runTest(dispatcher) {
        val viewModel = createViewModel(threeTextBlocks())
        advanceUntilIdle()

        viewModel.moveBlock(3L, 2L)
        advanceUntilIdle()

        val blocks = viewModel.uiState.value.blocks
        assertEquals("拖动向上时应落到目标块之前", listOf("A", "C", "B"), blocks.map { it.content })
        assertEquals("sortOrder 应按新顺序重算", listOf(0, 1, 2), blocks.map { it.sortOrder })
    }

    @Test
    fun `moveBlock keeps branch children attached to their branch`() = runTest(dispatcher) {
        val viewModel = createViewModel(noteWithBranchChildren())
        advanceUntilIdle()

        viewModel.moveBlock(2L, 4L)
        advanceUntilIdle()

        val blocks = viewModel.uiState.value.blocks
        assertEquals(
            "分支块移动时其子块必须跟随",
            listOf("A", "C", "分支", "子块"),
            blocks.map { it.content }
        )
        assertEquals(
            "子块仍应挂在原分支下",
            2L,
            blocks.first { it.content == "子块" }.parentBranchId
        )
    }

    @Test
    fun `moveBlock ignores blocks that live inside a branch`() = runTest(dispatcher) {
        val viewModel = createViewModel(noteWithBranchChildren())
        advanceUntilIdle()

        viewModel.moveBlock(3L, 4L)
        viewModel.moveBlock(1L, 3L)
        advanceUntilIdle()

        val blocks = viewModel.uiState.value.blocks
        assertEquals(
            "分支子块不参与顶层排序，顺序应保持不变",
            listOf("A", "分支", "子块", "C"),
            blocks.map { it.content }
        )
    }

    private fun threeTextBlocks(): Note = Note(
        id = 7L,
        title = "Reorder",
        blocks = listOf(
            Block(id = 1L, type = BlockType.TEXT, content = "A", sortOrder = 0),
            Block(id = 2L, type = BlockType.TEXT, content = "B", sortOrder = 1),
            Block(id = 3L, type = BlockType.TEXT, content = "C", sortOrder = 2)
        )
    )

    private fun noteWithBranchChildren(): Note = Note(
        id = 7L,
        title = "Reorder branch",
        blocks = listOf(
            Block(id = 1L, type = BlockType.TEXT, content = "A", sortOrder = 0),
            Block(id = 2L, type = BlockType.BRANCH, content = "分支", sortOrder = 1),
            Block(id = 3L, type = BlockType.TEXT, content = "子块", sortOrder = 2, parentBranchId = 2L),
            Block(id = 4L, type = BlockType.TEXT, content = "C", sortOrder = 3)
        )
    )

    private fun createViewModel(
        note: Note,
        clipboardManager: BlockClipboardManager = mockk(relaxed = true)
    ): NoteViewModel = NoteViewModel(
        noteRepository = BlockOpsTestNoteRepository(note),
        tagRepository = BlockOpsTestTagRepository(),
        reviewRepository = BlockOpsTestReviewRepository(),
        todoRepository = BlockOpsTestTodoRepository(),
        reminderRepository = BlockOpsTestReminderRepository(),
        mediaRepository = mockk(relaxed = true),
        blockClipboardManager = clipboardManager,
        aiTaskManager = fakeAiTaskManager(),
        refManager = fakeAiRefManager(),
        context = mockk(relaxed = true),
        savedStateHandle = SavedStateHandle(mapOf("noteId" to note.id))
    )
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

private class BlockOpsTestTagRepository : TagRepository {

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

private class BlockOpsTestReviewRepository : ReviewRepository {
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

private class BlockOpsTestReminderRepository : ReminderRepository {
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

private class BlockOpsTestTodoRepository : TodoRepository {

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
