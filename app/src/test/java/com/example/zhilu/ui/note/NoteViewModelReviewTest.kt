package com.example.zhilu.ui.note

import androidx.lifecycle.SavedStateHandle
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderStatus
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteViewModelReviewTest {
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
    fun startReviewCreatesPlanAndShowsNextReviewTime() = runTest(dispatcher) {
        val reviewRepository = ReviewTestReviewRepository()
        val reminderRepository = ReviewTestReminderRepository()
        val viewModel = NoteViewModel(
            noteRepository = ReviewTestNoteRepository(note = Note(id = 3, title = "linear algebra")),
            tagRepository = ReviewTestTagRepository(emptyList()),
            reviewRepository = reviewRepository,
            todoRepository = ReviewTestTodoRepository(),
            reminderRepository = reminderRepository,
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 3L))
        )

        advanceUntilIdle()
        viewModel.startReviewPlan(now = 1_000_000L)
        advanceUntilIdle()

        assertEquals(3L, viewModel.uiState.value.reviewPlan?.noteId)
        assertEquals(1_000_000L + 86_400_000L, viewModel.uiState.value.reviewPlan?.nextReviewAt)
        assertEquals(1, reminderRepository.scheduled.size)
        assertEquals(ReminderType.REVIEW, reminderRepository.scheduled.single().type)
    }

    @Test
    fun existingDueReviewPlanIsLoadedWithNote() = runTest(dispatcher) {
        val reviewRepository = ReviewTestReviewRepository(
            plan = ReviewPlan(id = 8L, noteId = 3L, nextReviewAt = 1L)
        )
        val reminderRepository = ReviewTestReminderRepository()
        val viewModel = NoteViewModel(
            noteRepository = ReviewTestNoteRepository(note = Note(id = 3, title = "linear algebra")),
            tagRepository = ReviewTestTagRepository(emptyList()),
            reviewRepository = reviewRepository,
            todoRepository = ReviewTestTodoRepository(),
            reminderRepository = reminderRepository,
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 3L))
        )

        advanceUntilIdle()

        assertEquals(8L, viewModel.uiState.value.reviewPlan?.id)
        assertEquals(true, viewModel.uiState.value.isReviewDue)
        assertEquals(1, reminderRepository.scheduled.size)
        assertEquals(8L, reminderRepository.scheduled.single().sourceId)
    }

    @Test
    fun existingReviewPlanWithFiredReminderDoesNotUpsertOnLoad() = runTest(dispatcher) {
        val existingReminder = ReminderInstance(
            id = 13L,
            type = ReminderType.REVIEW,
            sourceId = 8L,
            noteId = 3L,
            dueAt = 1L,
            status = ReminderStatus.FIRED,
            notificationId = "review-8".hashCode()
        )
        val reminderRepository = ReviewTestReminderRepository(activeReminder = existingReminder)
        val viewModel = NoteViewModel(
            noteRepository = ReviewTestNoteRepository(note = Note(id = 3, title = "linear algebra")),
            tagRepository = ReviewTestTagRepository(emptyList()),
            reviewRepository = ReviewTestReviewRepository(
                plan = ReviewPlan(id = 8L, noteId = 3L, nextReviewAt = 1L)
            ),
            todoRepository = ReviewTestTodoRepository(),
            reminderRepository = reminderRepository,
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 3L))
        )

        advanceUntilIdle()

        assertEquals(8L, viewModel.uiState.value.reviewPlan?.id)
        assertEquals(emptyList<ReminderInstance>(), reminderRepository.scheduled)
    }

    @Test
    fun existingReviewPlanWithoutActiveReminderUpsertsOnLoad() = runTest(dispatcher) {
        val reminderRepository = ReviewTestReminderRepository(activeReminder = null)
        NoteViewModel(
            noteRepository = ReviewTestNoteRepository(note = Note(id = 3, title = "linear algebra")),
            tagRepository = ReviewTestTagRepository(emptyList()),
            reviewRepository = ReviewTestReviewRepository(
                plan = ReviewPlan(id = 8L, noteId = 3L, nextReviewAt = 1L)
            ),
            todoRepository = ReviewTestTodoRepository(),
            reminderRepository = reminderRepository,
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 3L))
        )

        advanceUntilIdle()

        assertEquals(1, reminderRepository.scheduled.size)
        assertEquals(8L, reminderRepository.scheduled.single().sourceId)
    }

    @Test
    fun recordReviewUpdatesPlanAndSchedulesNextReminder() = runTest(dispatcher) {
        val startingPlan = ReviewPlan(id = 5L, noteId = 3L, nextReviewAt = 1L)
        val reviewRepository = ReviewTestReviewRepository(plan = startingPlan)
        val reminderRepository = ReviewTestReminderRepository()
        val viewModel = NoteViewModel(
            noteRepository = ReviewTestNoteRepository(note = Note(id = 3, title = "linear algebra")),
            tagRepository = ReviewTestTagRepository(emptyList()),
            reviewRepository = reviewRepository,
            todoRepository = ReviewTestTodoRepository(),
            reminderRepository = reminderRepository,
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 3L))
        )

        advanceUntilIdle()
        reminderRepository.scheduled.clear()
        viewModel.recordReview(ReviewRating.NORMAL, now = 2_000_000L)
        advanceUntilIdle()

        assertEquals(ReviewRating.NORMAL, reviewRepository.recordedRatings.single())
        assertEquals(2_000_000L + 86_400_000L, viewModel.uiState.value.reviewPlan?.nextReviewAt)
        assertEquals(1, reminderRepository.scheduled.size)
    }

    @Test
    fun rapidRecordReviewCallsOnlyRecordOnce() = runTest(dispatcher) {
        val startingPlan = ReviewPlan(id = 5L, noteId = 3L, nextReviewAt = 1L)
        val reviewRepository = ReviewTestReviewRepository(plan = startingPlan)
        val reminderRepository = ReviewTestReminderRepository()
        val viewModel = NoteViewModel(
            noteRepository = ReviewTestNoteRepository(note = Note(id = 3, title = "linear algebra")),
            tagRepository = ReviewTestTagRepository(emptyList()),
            reviewRepository = reviewRepository,
            todoRepository = ReviewTestTodoRepository(),
            reminderRepository = reminderRepository,
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 3L))
        )

        advanceUntilIdle()
        reminderRepository.scheduled.clear()
        viewModel.recordReview(ReviewRating.NORMAL, now = 2_000_000L)
        viewModel.recordReview(ReviewRating.HARD, now = 2_000_001L)
        advanceUntilIdle()

        assertEquals(listOf(ReviewRating.NORMAL), reviewRepository.recordedRatings)
        assertEquals(false, viewModel.uiState.value.isRecordingReview)
    }

    @Test
    fun recordReviewWithNoNextReviewCancelsExistingReminder() = runTest(dispatcher) {
        val startingPlan = ReviewPlan(id = 5L, noteId = 3L, nextReviewAt = 1L)
        val reminderRepository = ReviewTestReminderRepository()
        val viewModel = NoteViewModel(
            noteRepository = ReviewTestNoteRepository(note = Note(id = 3, title = "linear algebra")),
            tagRepository = ReviewTestTagRepository(emptyList()),
            reviewRepository = ReviewTestReviewRepository(
                plan = startingPlan,
                completeOnRecord = true
            ),
            todoRepository = ReviewTestTodoRepository(),
            reminderRepository = reminderRepository,
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 3L))
        )

        advanceUntilIdle()
        reminderRepository.scheduled.clear()
        viewModel.recordReview(ReviewRating.MASTERED, now = 2_000_000L)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.reviewPlan?.nextReviewAt)
        assertEquals(emptyList<ReminderInstance>(), reminderRepository.scheduled)
        assertEquals(listOf(ReminderType.REVIEW to 5L), reminderRepository.canceled)
    }

    @Test
    fun disableReviewPlanClearsStateAndCancelsReminder() = runTest(dispatcher) {
        val startingPlan = ReviewPlan(id = 5L, noteId = 3L, nextReviewAt = 1L)
        val reminderRepository = ReviewTestReminderRepository()
        val viewModel = NoteViewModel(
            noteRepository = ReviewTestNoteRepository(note = Note(id = 3, title = "linear algebra")),
            tagRepository = ReviewTestTagRepository(emptyList()),
            reviewRepository = ReviewTestReviewRepository(plan = startingPlan),
            todoRepository = ReviewTestTodoRepository(),
            reminderRepository = reminderRepository,
            savedStateHandle = SavedStateHandle(mapOf("noteId" to 3L))
        )

        advanceUntilIdle()
        viewModel.disableReviewPlan()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.reviewPlan?.nextReviewAt)
        assertFalse(viewModel.uiState.value.reviewPlan?.enabled ?: true)
        assertEquals(listOf(ReminderType.REVIEW to 5L), reminderRepository.canceled)
    }
}

private class ReviewTestReviewRepository(
    private var plan: ReviewPlan? = null,
    private val completeOnRecord: Boolean = false
) : ReviewRepository {
    val recordedRatings = mutableListOf<ReviewRating>()

    override suspend fun getPlanByNoteId(noteId: Long): RepositoryResult<ReviewPlan?> =
        RepositoryResult.Success(plan?.takeIf { it.noteId == noteId })

    override suspend fun startPlan(noteId: Long, now: Long): RepositoryResult<ReviewPlan> {
        val newPlan = ReviewPlan(
            id = 10L,
            noteId = noteId,
            currentStep = 1,
            nextReviewAt = now + 86_400_000L,
            createdAt = now,
            updatedAt = now
        )
        plan = newPlan
        return RepositoryResult.Success(newPlan)
    }

    override suspend fun recordReview(
        plan: ReviewPlan,
        rating: ReviewRating,
        reviewedAt: Long
    ): RepositoryResult<ReviewPlan> {
        recordedRatings += rating
        val updated = plan.copy(
            enabled = !completeOnRecord,
            currentStep = plan.currentStep + 1,
            nextReviewAt = if (completeOnRecord) null else reviewedAt + 86_400_000L,
            updatedAt = reviewedAt,
            completedAt = if (completeOnRecord) reviewedAt else null
        )
        this.plan = updated
        return RepositoryResult.Success(updated)
    }

    override suspend fun disablePlan(noteId: Long): RepositoryResult<Unit> {
        plan = plan?.takeIf { it.noteId == noteId }?.copy(enabled = false, nextReviewAt = null)
        return RepositoryResult.Success(Unit)
    }
}

private class ReviewTestReminderRepository(
    private val activeReminder: ReminderInstance? = null
) : ReminderRepository {
    val scheduled = mutableListOf<ReminderInstance>()
    val canceled = mutableListOf<Pair<ReminderType, Long>>()

    override fun observeAll(): Flow<RepositoryResult<List<ReminderInstance>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun getDueReminders(now: Long): RepositoryResult<List<ReminderInstance>> =
        RepositoryResult.Success(emptyList())

    override suspend fun getActiveReminder(
        type: ReminderType,
        sourceId: Long
    ): RepositoryResult<ReminderInstance?> =
        RepositoryResult.Success(activeReminder?.takeIf { it.type == type && it.sourceId == sourceId })

    override suspend fun upsertScheduled(reminder: ReminderInstance): RepositoryResult<Long> {
        scheduled += reminder
        return RepositoryResult.Success(reminder.id)
    }

    override suspend fun markDone(type: ReminderType, sourceId: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun markFired(reminder: ReminderInstance, firedAt: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun cancel(type: ReminderType, sourceId: Long): RepositoryResult<Unit> {
        canceled += type to sourceId
        return RepositoryResult.Success(Unit)
    }
}

private class ReviewTestTodoRepository : TodoRepository {
    override fun observeByNoteId(noteId: Long): Flow<RepositoryResult<List<TodoItem>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun addTodo(todo: TodoItem): RepositoryResult<Long> =
        RepositoryResult.Success(1L)

    override suspend fun updateTodo(todo: TodoItem): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun completeTodo(id: Long, completedAt: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)
}

private class ReviewTestTagRepository(
    private val tags: List<Tag>
) : TagRepository {
    override fun getAllTags(): Flow<RepositoryResult<List<Tag>>> =
        flowOf(RepositoryResult.Success(tags))

    override suspend fun getTagById(id: Long): RepositoryResult<Tag?> =
        RepositoryResult.Success(tags.firstOrNull { it.id == id })

    override suspend fun getTagByName(name: String): RepositoryResult<Tag?> =
        RepositoryResult.Success(tags.firstOrNull { it.name == name })

    override suspend fun getTagsByNoteId(noteId: Long): RepositoryResult<List<Tag>> =
        RepositoryResult.Success(emptyList())

    override suspend fun insertTag(tag: Tag): RepositoryResult<Long> =
        RepositoryResult.Success(tag.id)

    override suspend fun updateTag(tag: Tag): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun deleteTag(tag: Tag): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override fun getTagCount(): Flow<RepositoryResult<Int>> =
        flowOf(RepositoryResult.Success(tags.size))
}

private class ReviewTestNoteRepository(
    private val note: Note? = null
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

    override suspend fun insertNote(note: Note): RepositoryResult<Long> =
        RepositoryResult.Success(1L)

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
