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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import io.mockk.mockk
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
            mediaRepository = mockk(relaxed = true),
            blockClipboardManager = mockk(relaxed = true),
            aiTaskManager = fakeAiTaskManager(),
            refManager = fakeAiRefManager(),
            context = mockk(relaxed = true),
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 7L))
        )
        advanceUntilIdle()

        viewModel.addBlock(BlockType.LATEX)
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals(0, noteRepository.updatedNotes.size)
        assertTrue(viewModel.uiState.value.blocks.any { it.type == BlockType.LATEX })

        val latexBlock = viewModel.uiState.value.blocks.first { it.type == BlockType.LATEX }
        viewModel.onBlockContentChange(latexBlock.id, "\\alpha^2")
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals(1, noteRepository.updatedNotes.size)
        assertTrue(noteRepository.updatedNotes.single().blocks.any { it.type == BlockType.LATEX && it.content == "\\alpha^2" })
    }

    @Test
    fun setBlockLanguageUpdatesTargetBlockAndPersistsLanguage() = runTest(dispatcher) {
        val noteRepository = RecordingNoteRepository(
            note = Note(
                id = 7L,
                title = "Code note",
                blocks = listOf(
                    Block(type = BlockType.TEXT, content = "Intro", sortOrder = 0),
                    Block(type = BlockType.CODE, content = "println(\"hi\")", language = "kotlin", sortOrder = 1)
                )
            )
        )
        val viewModel = createViewModel(noteRepository)
        advanceUntilIdle()

        val codeBlock = viewModel.uiState.value.blocks[1]
        viewModel.setBlockLanguage(codeBlock.id, "java")
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals("java", viewModel.uiState.value.blocks.first { it.id == codeBlock.id }.language)
        assertEquals("java", noteRepository.updatedNotes.single().blocks.first { it.id == codeBlock.id }.language)
    }

    @Test
    fun moveBlockChangesOrderAndSavedSortOrder() = runTest(dispatcher) {
        val noteRepository = RecordingNoteRepository(
            note = Note(
                id = 7L,
                title = "Ordered note",
                blocks = listOf(
                    Block(type = BlockType.TEXT, content = "A", sortOrder = 0),
                    Block(type = BlockType.TEXT, content = "B", sortOrder = 1),
                    Block(type = BlockType.TEXT, content = "C", sortOrder = 2)
                )
            )
        )
        val viewModel = createViewModel(noteRepository)
        advanceUntilIdle()

        val blocks = viewModel.uiState.value.blocks
        viewModel.moveBlock(blocks[2].id, blocks[0].id)
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals(listOf("C", "A", "B"), viewModel.uiState.value.blocks.map { it.content })
        assertEquals(
            listOf("C" to 0, "A" to 1, "B" to 2),
            noteRepository.updatedNotes.single().blocks.map { it.content to it.sortOrder }
        )
    }

    @Test
    fun moveBlockReordersByBlockId() = runTest(dispatcher) {
        val noteRepository = RecordingNoteRepository(
            note = Note(
                id = 7L,
                title = "Append move note",
                blocks = listOf(
                    Block(type = BlockType.TEXT, content = "A", sortOrder = 0),
                    Block(type = BlockType.TEXT, content = "B", sortOrder = 1),
                    Block(type = BlockType.TEXT, content = "C", sortOrder = 2)
                )
            )
        )
        val viewModel = createViewModel(noteRepository)
        advanceUntilIdle()

        val blocks = viewModel.uiState.value.blocks
        viewModel.moveBlock(blocks[1].id, blocks[0].id)
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals(listOf("B", "A", "C"), viewModel.uiState.value.blocks.map { it.content })
        assertEquals(
            listOf("B" to 0, "A" to 1, "C" to 2),
            noteRepository.updatedNotes.single().blocks.map { it.content to it.sortOrder }
        )
    }

    @Test
    fun undoRemoveBlockRestoresDeletedBlockAtOriginalIndex() = runTest(dispatcher) {
        val noteRepository = RecordingNoteRepository(
            note = Note(
                id = 7L,
                title = "Undo note",
                blocks = listOf(
                    Block(type = BlockType.TEXT, content = "A", sortOrder = 0),
                    Block(type = BlockType.TEXT, content = "B", sortOrder = 1),
                    Block(type = BlockType.TEXT, content = "C", sortOrder = 2)
                )
            )
        )
        val viewModel = createViewModel(noteRepository)
        advanceUntilIdle()

        viewModel.removeBlock(viewModel.uiState.value.blocks[1].id)
        viewModel.undoRemoveBlock()

        assertEquals(listOf("A", "B", "C"), viewModel.uiState.value.blocks.map { it.content })
    }

    @Test
    fun quickDeleteConfirmForFirstEventDoesNotClearSecondUndo() = runTest(dispatcher) {
        val noteRepository = RecordingNoteRepository(
            note = Note(
                id = 7L,
                title = "Queued undo note",
                blocks = listOf(
                    Block(type = BlockType.TEXT, content = "A", sortOrder = 0),
                    Block(type = BlockType.TEXT, content = "B", sortOrder = 1),
                    Block(type = BlockType.TEXT, content = "C", sortOrder = 2)
                )
            )
        )
        val viewModel = createViewModel(noteRepository)
        advanceUntilIdle()
        val events = mutableListOf<UiEvent.ShowUndoSnackbar>()
        val eventJob = launch {
            viewModel.uiEvents.take(2).collect { event ->
                events += event as UiEvent.ShowUndoSnackbar
            }
        }

        viewModel.removeBlock(viewModel.uiState.value.blocks[0].id)
        viewModel.removeBlock(viewModel.uiState.value.blocks[0].id)
        runCurrent()
        viewModel.confirmRemoveBlock(events[0].token)
        viewModel.undoRemoveBlock(events[1].token)
        eventJob.cancel()

        assertEquals(listOf("B", "C"), viewModel.uiState.value.blocks.map { it.content })
    }

    @Test
    fun deletingLastMeaningfulBlockFromExistingUntitledNotePersistsBlankState() = runTest(dispatcher) {
        val noteRepository = RecordingNoteRepository(
            note = Note(
                id = 7L,
                title = "",
                blocks = listOf(Block(type = BlockType.TEXT, content = "Only content", sortOrder = 0))
            )
        )
        val viewModel = createViewModel(noteRepository)
        advanceUntilIdle()

        viewModel.removeBlock(viewModel.uiState.value.blocks[0].id)
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals(1, noteRepository.updatedNotes.size)
        assertEquals("", noteRepository.updatedNotes.single().title)
        assertEquals(listOf(""), noteRepository.updatedNotes.single().blocks.map { it.content })
    }

    @Test
    fun newEditDoesNotCancelInFlightSaveAndStatusWaitsForLatestSave() = runTest(dispatcher) {
        val noteRepository = RecordingNoteRepository(
            note = Note(
                id = 7L,
                title = "Save note",
                blocks = listOf(Block(type = BlockType.TEXT, content = "Before", sortOrder = 0))
            ),
            updateDelayMillis = 1_000
        )
        val viewModel = createViewModel(noteRepository)
        advanceUntilIdle()

        val blockId = viewModel.uiState.value.blocks[0].id
        viewModel.onBlockContentChange(blockId, "First")
        advanceTimeBy(500)
        runCurrent()
        viewModel.onBlockContentChange(blockId, "Second")
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf("First"), noteRepository.updatedNotes.map { it.blocks.single().content })
        assertEquals(SaveStatus.SAVING, viewModel.uiState.value.saveStatus)

        advanceTimeBy(500)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf("First", "Second"), noteRepository.updatedNotes.map { it.blocks.single().content })
        assertEquals(SaveStatus.SAVED, viewModel.uiState.value.saveStatus)
        assertEquals(false, viewModel.uiState.value.isSaving)
    }

    @Test
    fun overlappingAutosavesForNewNoteInsertOnceThenUpdateAssignedNote() = runTest(dispatcher) {
        val noteRepository = RecordingNoteRepository(
            note = Note(id = 7L, title = "", blocks = emptyList()),
            insertDelayMillis = 1_000
        )
        val viewModel = createNewNoteViewModel(noteRepository)

        val blockId = viewModel.uiState.value.blocks[0].id
        viewModel.onBlockContentChange(blockId, "First")
        advanceTimeBy(500)
        runCurrent()
        viewModel.onBlockContentChange(blockId, "Second")
        advanceTimeBy(500)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf("First"), noteRepository.insertedNotes.map { it.blocks.single().content })
        assertEquals(listOf("Second"), noteRepository.updatedNotes.map { it.blocks.single().content })
        assertEquals(1L, viewModel.uiState.value.noteId)
        assertEquals(SaveStatus.SAVED, viewModel.uiState.value.saveStatus)
    }

    @Test
    fun todoCreationDuringNewNoteAutosaveReusesInsertedNote() = runTest(dispatcher) {
        val noteRepository = RecordingNoteRepository(
            note = Note(id = 7L, title = "", blocks = emptyList()),
            insertDelayMillis = 1_000
        )
        val viewModel = createNewNoteViewModel(noteRepository)
        val todoResults = mutableListOf<Boolean>()

        viewModel.onBlockContentChange(viewModel.uiState.value.blocks[0].id, "Draft")
        advanceTimeBy(500)
        runCurrent()
        val todoJob = launch {
            todoResults += viewModel.createTodo("Follow up", remindAt = null)
        }
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        todoJob.join()

        assertEquals(listOf("Draft"), noteRepository.insertedNotes.map { it.blocks.single().content })
        assertEquals(listOf(true), todoResults)
        assertEquals(1L, viewModel.uiState.value.noteId)
    }

    @Test
    fun successfulDebouncedSaveTransitionsToSavedStatus() = runTest(dispatcher) {
        val noteRepository = RecordingNoteRepository(
            note = Note(
                id = 7L,
                title = "Save note",
                blocks = listOf(Block(type = BlockType.TEXT, content = "Before", sortOrder = 0))
            ),
            updateDelayMillis = 1_000
        )
        val viewModel = createViewModel(noteRepository)
        advanceUntilIdle()

        viewModel.onBlockContentChange(viewModel.uiState.value.blocks[0].id, "After")
        advanceTimeBy(500)
        runCurrent()
        assertEquals(SaveStatus.SAVING, viewModel.uiState.value.saveStatus)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(SaveStatus.SAVED, viewModel.uiState.value.saveStatus)
        assertEquals(false, viewModel.uiState.value.isSaving)
        assertEquals("After", noteRepository.updatedNotes.single().blocks.single().content)
    }

    private fun createViewModel(noteRepository: RecordingNoteRepository): NoteViewModel =
        NoteViewModel(
            noteRepository = noteRepository,
            tagRepository = EmptyTagRepository(),
            reviewRepository = AdvancedBlockReviewRepository(),
            todoRepository = AdvancedBlockTodoRepository(),
            reminderRepository = AdvancedBlockReminderRepository(),
            mediaRepository = mockk(relaxed = true),
            blockClipboardManager = mockk(relaxed = true),
            aiTaskManager = fakeAiTaskManager(),
            refManager = fakeAiRefManager(),
            context = mockk(relaxed = true),
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 7L))
        )

    private fun createNewNoteViewModel(noteRepository: RecordingNoteRepository): NoteViewModel =
        NoteViewModel(
            noteRepository = noteRepository,
            tagRepository = EmptyTagRepository(),
            reviewRepository = AdvancedBlockReviewRepository(),
            todoRepository = AdvancedBlockTodoRepository(),
            reminderRepository = AdvancedBlockReminderRepository(),
            mediaRepository = mockk(relaxed = true),
            blockClipboardManager = mockk(relaxed = true),
            aiTaskManager = fakeAiTaskManager(),
            refManager = fakeAiRefManager(),
            context = mockk(relaxed = true),
            savedStateHandle = SavedStateHandle()
        )

    @Test
    fun setDraggingPausesAutosave() = runTest(dispatcher) {
        val noteRepository = RecordingNoteRepository(
            note = Note(
                id = 7L,
                title = "Drag test",
                blocks = listOf(
                    Block(type = BlockType.TEXT, content = "A", sortOrder = 0),
                    Block(type = BlockType.TEXT, content = "B", sortOrder = 1)
                )
            )
        )
        val viewModel = NoteViewModel(
            noteRepository = noteRepository,
            tagRepository = EmptyTagRepository(),
            reviewRepository = AdvancedBlockReviewRepository(),
            todoRepository = AdvancedBlockTodoRepository(),
            reminderRepository = AdvancedBlockReminderRepository(),
            mediaRepository = mockk(relaxed = true),
            blockClipboardManager = mockk(relaxed = true),
            aiTaskManager = fakeAiTaskManager(),
            refManager = fakeAiRefManager(),
            context = mockk(relaxed = true),
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 7L))
        )
        advanceUntilIdle()
        noteRepository.updatedNotes.clear()

        val firstBlock = viewModel.uiState.value.blocks[0]
        viewModel.setDragging(true)
        viewModel.onBlockContentChange(firstBlock.id, "Changed while dragging")
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals("scheduleSave should be skipped while dragging", 0, noteRepository.updatedNotes.size)

        viewModel.setDragging(false)
        viewModel.onBlockContentChange(firstBlock.id, "Final content")
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals("save should fire after drag ends", 1, noteRepository.updatedNotes.size)
    }

    @Test
    fun moveBlockStillPersistsWhenNotDragging() = runTest(dispatcher) {
        val noteRepository = RecordingNoteRepository(
            note = Note(
                id = 7L,
                title = "Move test",
                blocks = listOf(
                    Block(type = BlockType.TEXT, content = "A", sortOrder = 0),
                    Block(type = BlockType.TEXT, content = "B", sortOrder = 1)
                )
            )
        )
        val viewModel = NoteViewModel(
            noteRepository = noteRepository,
            tagRepository = EmptyTagRepository(),
            reviewRepository = AdvancedBlockReviewRepository(),
            todoRepository = AdvancedBlockTodoRepository(),
            reminderRepository = AdvancedBlockReminderRepository(),
            mediaRepository = mockk(relaxed = true),
            blockClipboardManager = mockk(relaxed = true),
            aiTaskManager = fakeAiTaskManager(),
            refManager = fakeAiRefManager(),
            context = mockk(relaxed = true),
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 7L))
        )
        advanceUntilIdle()
        noteRepository.updatedNotes.clear()

        val blocks = viewModel.uiState.value.blocks
        viewModel.moveBlock(blocks[1].id, blocks[0].id)
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals("moveBlock should trigger save when not dragging", 1, noteRepository.updatedNotes.size)
        assertEquals(listOf("B", "A"), noteRepository.updatedNotes.single().blocks.map { it.content })
    }

    @Test
    fun moveBlockSkipsScheduleSaveWhenDragging() = runTest(dispatcher) {
        val noteRepository = RecordingNoteRepository(
            note = Note(
                id = 7L,
                title = "Drag move test",
                blocks = listOf(
                    Block(type = BlockType.TEXT, content = "A", sortOrder = 0),
                    Block(type = BlockType.TEXT, content = "B", sortOrder = 1)
                )
            )
        )
        val viewModel = NoteViewModel(
            noteRepository = noteRepository,
            tagRepository = EmptyTagRepository(),
            reviewRepository = AdvancedBlockReviewRepository(),
            todoRepository = AdvancedBlockTodoRepository(),
            reminderRepository = AdvancedBlockReminderRepository(),
            mediaRepository = mockk(relaxed = true),
            blockClipboardManager = mockk(relaxed = true),
            aiTaskManager = fakeAiTaskManager(),
            refManager = fakeAiRefManager(),
            context = mockk(relaxed = true),
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 7L))
        )
        advanceUntilIdle()
        noteRepository.updatedNotes.clear()

        val blocks = viewModel.uiState.value.blocks
        viewModel.setDragging(true)
        viewModel.moveBlock(blocks[1].id, blocks[0].id)
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals("moveBlock should skip save when dragging", 0, noteRepository.updatedNotes.size)
        assertEquals(listOf("B", "A"), viewModel.uiState.value.blocks.map { it.content })
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

    override fun getNoteCountsByTag(): Flow<RepositoryResult<Map<Long, Int>>> =
        flowOf(RepositoryResult.Success(emptyMap()))
}

private class RecordingNoteRepository(
    private val note: Note,
    private val updateDelayMillis: Long = 0L,
    private val insertDelayMillis: Long = 0L
) : NoteRepository {
    val insertedNotes = mutableListOf<Note>()
    val updatedNotes = mutableListOf<Note>()

    override fun getAllNotes(): Flow<RepositoryResult<List<Note>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override fun getFavoriteNotes(): Flow<RepositoryResult<List<Note>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override fun getDeletedNotes(): Flow<RepositoryResult<List<Note>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun getNoteById(id: Long): RepositoryResult<Note?> {
        val returnedNote = note.takeIf { it.id == id }?.let { n ->
            n.copy(
                blocks = n.blocks.mapIndexed { index, block ->
                    if (block.id == 0L) block.copy(id = index + 1L) else block
                }
            )
        }
        return RepositoryResult.Success(returnedNote)
    }

    override suspend fun searchNotes(keyword: String): RepositoryResult<List<Note>> =
        RepositoryResult.Success(emptyList())

    override suspend fun getNotesByTagId(tagId: Long): RepositoryResult<List<Note>> =
        RepositoryResult.Success(emptyList())

    override suspend fun insertNote(note: Note): RepositoryResult<Note> {
        insertedNotes += note
        if (insertDelayMillis > 0L) {
            delay(insertDelayMillis)
        }
        return RepositoryResult.Success(note.copy(id = 1L))
    }

    override suspend fun updateNote(note: Note): RepositoryResult<Note> {
        if (updateDelayMillis > 0L) {
            delay(updateDelayMillis)
        }
        updatedNotes += note
        return RepositoryResult.Success(note)
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
