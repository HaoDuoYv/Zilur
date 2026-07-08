package com.example.zhilu.ui.reminder

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderStatus
import com.example.zhilu.domain.model.ReminderType
import com.example.zhilu.domain.repository.ReminderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class ReminderCenterViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val startOfToday = 1_000_000L
    private val now = startOfToday + 2_000L
    private val day = 86_400_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun observesRemindersAndGroupsIntoUiState() = runTest(dispatcher) {
        val overdue = reminder(id = 1, dueAt = now - 1)
        val today = reminder(id = 2, dueAt = now + 1)
        val future = reminder(id = 3, dueAt = startOfToday + day)
        val completed = reminder(id = 4, dueAt = now, status = ReminderStatus.DONE)
        val repository = FakeReminderRepository(
            initial = listOf(future, completed, overdue, today)
        )

        val viewModel = viewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals(null, state.error)
        assertEquals(listOf(overdue), state.overdue)
        assertEquals(listOf(today), state.today)
        assertEquals(listOf(future), state.future)
        assertEquals(listOf(completed), state.completed)
    }

    @Test
    fun markDoneDelegatesReminderIdentityToRepository() = runTest(dispatcher) {
        val repository = FakeReminderRepository()
        val viewModel = viewModel(repository)
        val reminder = reminder(id = 7, type = ReminderType.TODO, sourceId = 55L)

        viewModel.markDone(reminder)
        advanceUntilIdle()

        assertEquals(listOf(ReminderType.TODO to 55L), repository.doneCalls)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun markDoneSuccessMovesReminderToCompletedWithoutNewRepositoryEmission() = runTest(dispatcher) {
        val reminder = reminder(id = 7, type = ReminderType.TODO, sourceId = 55L, dueAt = now + 1)
        val repository = FakeReminderRepository(initial = listOf(reminder))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.markDone(reminder)
        advanceUntilIdle()

        val expectedDone = reminder.copy(status = ReminderStatus.DONE, updatedAt = now)
        assertEquals(emptyList<ReminderInstance>(), viewModel.uiState.value.today)
        assertEquals(listOf(expectedDone), viewModel.uiState.value.completed)
    }

    @Test
    fun cancelDelegatesReminderIdentityToRepository() = runTest(dispatcher) {
        val repository = FakeReminderRepository()
        val viewModel = viewModel(repository)
        val reminder = reminder(id = 8, type = ReminderType.REVIEW, sourceId = 66L)

        viewModel.cancel(reminder)
        advanceUntilIdle()

        assertEquals(listOf(ReminderType.REVIEW to 66L), repository.cancelCalls)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun cancelSuccessRemovesActiveReminderWithoutAddingCompleted() = runTest(dispatcher) {
        val reminder = reminder(id = 8, type = ReminderType.REVIEW, sourceId = 66L, dueAt = now + 1)
        val repository = FakeReminderRepository(initial = listOf(reminder))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.cancel(reminder)
        advanceUntilIdle()

        assertEquals(emptyList<ReminderInstance>(), viewModel.uiState.value.today)
        assertEquals(emptyList<ReminderInstance>(), viewModel.uiState.value.completed)
    }

    @Test
    fun actionErrorsAreExposedInUiState() = runTest(dispatcher) {
        val repository = FakeReminderRepository(doneResult = RepositoryResult.Error("Done failed"))
        val viewModel = viewModel(repository)

        viewModel.markDone(reminder(id = 9))
        advanceUntilIdle()

        assertEquals("Done failed", viewModel.uiState.value.error)
    }

    private fun viewModel(repository: ReminderRepository) = ReminderCenterViewModel(
        reminderRepository = repository,
        nowProvider = { now },
        startOfTodayProvider = { startOfToday }
    )

    private fun reminder(
        id: Long,
        type: ReminderType = ReminderType.REVIEW,
        sourceId: Long = id,
        dueAt: Long = now,
        status: ReminderStatus = ReminderStatus.SCHEDULED
    ) = ReminderInstance(
        id = id,
        type = type,
        sourceId = sourceId,
        noteId = id,
        dueAt = dueAt,
        status = status,
        notificationId = id.toInt(),
        updatedAt = id
    )
}

private class FakeReminderRepository(
    initial: List<ReminderInstance> = emptyList(),
    private val doneResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit),
    private val cancelResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
) : ReminderRepository {
    private val reminders = MutableStateFlow<RepositoryResult<List<ReminderInstance>>>(RepositoryResult.Success(initial))
    val doneCalls = mutableListOf<Pair<ReminderType, Long>>()
    val cancelCalls = mutableListOf<Pair<ReminderType, Long>>()

    override fun observeAll(): Flow<RepositoryResult<List<ReminderInstance>>> = reminders

    override suspend fun getDueReminders(now: Long): RepositoryResult<List<ReminderInstance>> =
        RepositoryResult.Success(emptyList())

    override suspend fun getActiveReminder(
        type: ReminderType,
        sourceId: Long
    ): RepositoryResult<ReminderInstance?> =
        RepositoryResult.Success(null)

    override suspend fun upsertScheduled(reminder: ReminderInstance): RepositoryResult<Long> =
        RepositoryResult.Success(reminder.id)

    override suspend fun markDone(type: ReminderType, sourceId: Long): RepositoryResult<Unit> {
        doneCalls += type to sourceId
        return doneResult
    }

    override suspend fun markFired(reminder: ReminderInstance, firedAt: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun cancel(type: ReminderType, sourceId: Long): RepositoryResult<Unit> {
        cancelCalls += type to sourceId
        return cancelResult
    }
}
