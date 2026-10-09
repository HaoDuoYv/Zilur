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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteViewModelKnowledgeCardTest {
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
    fun focusCardSwitchesActiveCardAndPersistsCurrentBlocks() = runTest(dispatcher) {
        val note = noteWithTwoCards()
        val viewModel = createViewModel(note)
        advanceUntilIdle()

        viewModel.startEditing()
        advanceUntilIdle()

        val firstCard = viewModel.uiState.value.cards[0]
        val secondCard = viewModel.uiState.value.cards[1]

        viewModel.addBlock(BlockType.TEXT)
        advanceUntilIdle()

        val blocksBeforeSwitch = viewModel.uiState.value.blocks
        assertEquals(listOf("A", ""), blocksBeforeSwitch.map { it.content })

        viewModel.focusCard(secondCard.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("活跃卡片应切换到第二张", secondCard.id, state.activeCardId)
        assertFalse("第一张卡片应失焦", state.cards[0].isFocused)
        assertTrue("第二张卡片应聚焦", state.cards[1].isFocused)
        assertEquals(
            "切换前第一张卡片的块应被保存到该卡片",
            listOf("A", ""),
            state.cards[0].blocks.map { it.content }
        )
        assertEquals(
            "当前编辑状态应显示第二张卡片的块",
            listOf("B"),
            state.blocks.map { it.content }
        )
        assertEquals(
            "第二张卡片的块不应被修改",
            listOf("B"),
            state.cards[1].blocks.map { it.content }
        )
    }

    @Test
    fun focusSameCardKeepsStateUnchanged() = runTest(dispatcher) {
        val note = noteWithTwoCards()
        val viewModel = createViewModel(note)
        advanceUntilIdle()

        viewModel.startEditing()
        advanceUntilIdle()

        val firstCardId = viewModel.uiState.value.cards[0].id
        viewModel.focusCard(firstCardId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(firstCardId, state.activeCardId)
        assertTrue(state.cards[0].isFocused)
    }

    @Test
    fun addKnowledgeCardCreatesFocusedCardAndSynchronizesBlocks() = runTest(dispatcher) {
        val note = noteWithSingleCard()
        val viewModel = createViewModel(note)
        advanceUntilIdle()

        viewModel.startEditing()
        advanceUntilIdle()

        viewModel.addBlock(BlockType.TEXT)
        advanceUntilIdle()

        val previousCardId = viewModel.uiState.value.activeCardId
        val previousBlocks = viewModel.uiState.value.blocks.map { it.content }

        viewModel.addKnowledgeCard()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("应存在两张卡片（原卡片、新卡片）", 2, state.cards.size)
        assertNotNull("新建卡片后应有活跃卡片", state.activeCardId)

        val newCard = state.cards.last()
        assertTrue("新建卡片应处于聚焦状态", newCard.isFocused)
        assertEquals("活跃卡片 ID 应为新卡片 ID", newCard.id, state.activeCardId)
        assertEquals("新建卡片的块应为默认空白文本块", 1, newCard.blocks.size)
        assertEquals("", newCard.blocks.single().content)

        val previousCard = state.cards.first { it.id == previousCardId }
        assertFalse("原卡片应失焦", previousCard.isFocused)
        assertEquals(
            "原卡片的块应保留切换前的内容",
            previousBlocks,
            previousCard.blocks.map { it.content }
        )
        assertEquals(
            "当前编辑状态应同步为新卡片的块",
            listOf(""),
            state.blocks.map { it.content }
        )
    }

    @Test
    fun branchBlockOperationsWorkEndToEnd() = runTest(dispatcher) {
        val note = noteWithSingleCard()
        val viewModel = createViewModel(note)
        advanceUntilIdle()

        viewModel.startEditing()
        advanceUntilIdle()

        viewModel.addBlock(BlockType.BRANCH)
        advanceUntilIdle()

        val branchBlock = viewModel.uiState.value.blocks.find { it.type == BlockType.BRANCH }
        assertNotNull("应添加分支块", branchBlock)
        val branchId = branchBlock!!.id
        assertNull("分支块本身不应有 parentBranchId", branchBlock.parentBranchId)
        assertTrue(
            "编辑模式下分支块默认展开",
            viewModel.uiState.value.branchExpandedStates[branchId] == true
        )

        viewModel.toggleBranchExpanded(branchId)
        advanceUntilIdle()
        assertTrue(
            "点击后分支应收起",
            viewModel.uiState.value.branchExpandedStates[branchId] == false
        )

        viewModel.addBranchChildBlock(branchId, BlockType.TEXT)
        advanceUntilIdle()

        val childBlock = viewModel.uiState.value.blocks.find {
            it.type == BlockType.TEXT && it.parentBranchId == branchId
        }
        assertNotNull("应在分支下添加子块", childBlock)
        val state = viewModel.uiState.value
        val branchIndex = state.blocks.indexOfFirst { it.id == branchId }
        val childIndex = state.blocks.indexOfFirst { it.id == childBlock!!.id }
        assertTrue("子块应紧跟在分支块之后", childIndex == branchIndex + 1)

        viewModel.removeBlock(branchId)
        advanceUntilIdle()

        val afterRemove = viewModel.uiState.value
        assertNull("分支块应被删除", afterRemove.blocks.find { it.id == branchId })
        assertNull("子块应级联删除", afterRemove.blocks.find { it.id == childBlock!!.id })
        assertNull("分支展开状态应被清理", afterRemove.branchExpandedStates[branchId])
    }

    /**
     * 保存**不能**改动卡片 / 块的 id。
     *
     * 编辑页 `LazyColumn` 的 key 就是 `card.id`。一旦保存把本地临时 id 换成数据库主键，
     * key 就变了，正在输入的那个 `BasicTextField` 会被 Compose 当成新条目销毁重建：
     * 焦点丢失 → 键盘被收起。自动保存每 500ms 一次，表现就是「打字打到一半键盘自己没了」。
     *
     * 这条用例同时守着「不回写也不会破坏持久化」的另一半：第二次保存必须走 update 而不是
     * 又插一条——`NoteRepositoryImpl.updateNote` 是「按 noteId 全删再插」，
     * 主键由数据库重新分配，所以本地根本不需要记住上一次返回的主键，只要记住 noteId。
     */
    /**
     * 从浏览态进编辑态，**不能**凭空多出一张卡。
     *
     * 浏览态下 `currentCardId` 还是 init 里的临时负值，startEditing 里如果直接
     * `focusCard(第一张卡)`，会走到「切换卡片」分支并触发 `ensureCurrentCardExists`
     * 误判「当前卡片不存在」，兜出一张 title 同笔记标题的空卡——每进一次编辑就多一张。
     * 修复后 startEditing 先把 currentCardId 对齐到真实卡片，focusCard 命中快速路径。
     */
    @Test
    fun startEditingDoesNotDuplicateCards() = runTest(dispatcher) {
        val note = noteWithTwoCards()
        val viewModel = createViewModel(note)
        advanceUntilIdle()

        val cardsBefore = viewModel.uiState.value.cards.size
        assertEquals("初始应有两张卡片", 2, cardsBefore)

        viewModel.startEditing()
        advanceUntilIdle()

        assertEquals(
            "进入编辑态不应新增卡片",
            cardsBefore,
            viewModel.uiState.value.cards.size
        )
    }

    @Test
    fun saveKeepsLocalCardAndBlockIdsStable() = runTest(dispatcher) {
        val noteRepository = IdAssigningNoteRepository(note = null)
        val viewModel = NoteViewModel(
            noteRepository = noteRepository,
            tagRepository = KnowledgeCardTestTagRepository(),
            reviewRepository = KnowledgeCardTestReviewRepository(),
            todoRepository = KnowledgeCardTestTodoRepository(),
            reminderRepository = KnowledgeCardTestReminderRepository(),
            mediaRepository = mockk(relaxed = true),
            blockClipboardManager = mockk(relaxed = true),
            aiTaskManager = fakeAiTaskManager(),
            refManager = fakeAiRefManager(),
            context = mockk(relaxed = true),
            savedStateHandle = SavedStateHandle()
        )
        advanceUntilIdle()

        viewModel.startEditing()
        advanceUntilIdle()

        viewModel.onTitleChange("Persist")
        advanceUntilIdle()

        viewModel.addBlock(BlockType.BRANCH)
        advanceUntilIdle()

        val branchId = viewModel.uiState.value.blocks.first { it.type == BlockType.BRANCH }.id
        assertTrue("分支块 ID 应为临时负数", branchId < 0)

        viewModel.addBranchChildBlock(branchId, BlockType.TEXT)
        advanceUntilIdle()

        val childId = viewModel.uiState.value.blocks.first {
            it.type == BlockType.TEXT && it.parentBranchId == branchId
        }.id
        assertTrue("子块 ID 应为临时负数", childId < 0)

        val cardIdBeforeSave = viewModel.uiState.value.cards.single().id

        viewModel.saveNow()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // 笔记 id 是唯一需要采纳的返回值：它决定下次走 insert 还是 update。
        assertTrue("保存后笔记 ID 应为正数", state.noteId > 0)
        assertEquals("活跃卡片数量不变", 1, state.cards.size)

        val savedCard = state.cards.single()
        assertEquals(
            "卡片 ID 必须原地不动：它是 LazyColumn 的 key，变了就会把输入框换掉（键盘被收起）",
            cardIdBeforeSave,
            savedCard.id
        )
        assertEquals("活跃卡片 ID 应仍指向同一张卡", savedCard.id, state.activeCardId)

        val savedBranch = savedCard.blocks.find { it.type == BlockType.BRANCH }
        assertNotNull(savedBranch)
        assertEquals(
            "分支块 ID 必须原地不动",
            branchId,
            savedBranch!!.id
        )

        val savedChild = savedCard.blocks.find {
            it.type == BlockType.TEXT && it.parentBranchId != null
        }
        assertNotNull(savedChild)
        assertEquals("子块 ID 必须原地不动", childId, savedChild!!.id)
        assertEquals(
            "子块的 parentBranchId 仍指向本地分支 ID（落库时由 Repository 重新映射）",
            branchId,
            savedChild.parentBranchId
        )

        // 后续保存必须走 update：证明不回写主键也不会把笔记插重。
        // 这里不断言 update 的具体次数——自动保存是个循环，测试调度器下 advanceUntilIdle()
        // 会把它跑干，次数取决于调度节奏而不是逻辑正确性；要守的是「只插一次 + 一直用同一个 id」。
        viewModel.onTitleChange("Persist again")
        advanceUntilIdle()
        assertEquals("全程只应插入一次：不回写主键不会把笔记插重", 1, noteRepository.insertCount)
        assertTrue("首次之后的保存都应走 update", noteRepository.updateCount >= 1)
        assertEquals(
            "update 必须始终带上首次 insert 返回的笔记 ID",
            100L,
            noteRepository.lastUpdatedNoteId
        )
        assertEquals("笔记 ID 不应再变", state.noteId, viewModel.uiState.value.noteId)
    }

    @Test
    fun removeKnowledgeCardRemovesCardAndFocusesRemaining() = runTest(dispatcher) {
        val note = noteWithTwoCards()
        val viewModel = createViewModel(note)
        advanceUntilIdle()

        viewModel.startEditing()
        advanceUntilIdle()

        val firstCard = viewModel.uiState.value.cards[0]
        val secondCard = viewModel.uiState.value.cards[1]

        viewModel.focusCard(secondCard.id)
        advanceUntilIdle()

        viewModel.removeKnowledgeCard(secondCard.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("删除后应只剩一张卡片（不再有兜底卡片）", 1, state.cards.size)
        assertEquals("剩余卡片应为第一张", firstCard.id, state.cards[0].id)
        assertEquals("活跃卡片应切换到剩余第一张", firstCard.id, state.activeCardId)
        assertTrue("剩余卡片应处于聚焦状态", state.cards[0].isFocused)
    }

    @Test
    fun removeLastKnowledgeCardFallsBackToPreservedBlocks() = runTest(dispatcher) {
        val note = noteWithSingleCard()
        val viewModel = createViewModel(note)
        advanceUntilIdle()

        viewModel.startEditing()
        advanceUntilIdle()

        val firstCard = viewModel.uiState.value.cards[0]
        viewModel.removeKnowledgeCard(firstCard.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("原卡片删除后应只剩兜底卡片", 1, state.cards.size)
        assertTrue("兜底卡片应处于聚焦状态", state.cards[0].isFocused)
    }

    private fun createViewModel(note: Note): NoteViewModel = NoteViewModel(
        noteRepository = KnowledgeCardTestNoteRepository(note),
        tagRepository = KnowledgeCardTestTagRepository(),
        reviewRepository = KnowledgeCardTestReviewRepository(),
        todoRepository = KnowledgeCardTestTodoRepository(),
        reminderRepository = KnowledgeCardTestReminderRepository(),
        mediaRepository = mockk(relaxed = true),
        blockClipboardManager = mockk(relaxed = true),
        aiTaskManager = fakeAiTaskManager(),
        refManager = fakeAiRefManager(),
        context = mockk(relaxed = true),
        savedStateHandle = SavedStateHandle(mapOf("noteId" to note.id))
    )

    private fun noteWithSingleCard(): Note = Note(
        id = 12L,
        title = "单卡片笔记",
        cards = listOf(
            KnowledgeCard(
                id = 1L,
                title = "卡片1",
                blocks = listOf(Block(id = 10L, type = BlockType.TEXT, content = "A", sortOrder = 0))
            )
        )
    )

    private fun noteWithTwoCards(): Note = Note(
        id = 12L,
        title = "双卡片笔记",
        cards = listOf(
            KnowledgeCard(
                id = 1L,
                title = "卡片1",
                blocks = listOf(Block(id = 10L, type = BlockType.TEXT, content = "A", sortOrder = 0))
            ),
            KnowledgeCard(
                id = 2L,
                title = "卡片2",
                blocks = listOf(Block(id = 11L, type = BlockType.TEXT, content = "B", sortOrder = 0))
            )
        )
    )
}

private class KnowledgeCardTestNoteRepository(
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

private class IdAssigningNoteRepository(
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

    /** 记录走的哪条路径：第一次保存是 insert，之后必须改走 update。 */
    var insertCount = 0
    var updateCount = 0

    /** 最后一次 update 带进去的 noteId——它必须一直是首次 insert 返回的那一个。 */
    var lastUpdatedNoteId: Long? = null

    override suspend fun insertNote(note: Note): RepositoryResult<Note> {
        insertCount += 1
        return RepositoryResult.Success(assignIds(note, noteId = 100L))
    }

    override suspend fun updateNote(note: Note): RepositoryResult<Note> {
        updateCount += 1
        lastUpdatedNoteId = note.id
        return RepositoryResult.Success(assignIds(note, noteId = note.id))
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

    private fun assignIds(note: Note, noteId: Long): Note {
        var nextBlockId = 1L
        val savedCards = note.cards.mapIndexed { index, card ->
            val cardId = 1000L + index
            val savedBlocks = card.blocks.map { block ->
                block.copy(
                    id = nextBlockId++,
                    cardId = cardId,
                    noteId = noteId
                )
            }
            card.copy(
                id = cardId,
                blocks = savedBlocks
            )
        }
        val savedBlocks = savedCards.flatMap { it.blocks }
        return note.copy(
            id = noteId,
            cards = savedCards,
            blocks = savedBlocks
        )
    }
}

private class KnowledgeCardTestTagRepository : TagRepository {

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

private class KnowledgeCardTestReviewRepository : ReviewRepository {
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

private class KnowledgeCardTestReminderRepository : ReminderRepository {
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

private class KnowledgeCardTestTodoRepository : TodoRepository {

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
