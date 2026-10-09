package com.example.zhilu.domain.usecase

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderStatus
import com.example.zhilu.domain.model.ReminderType
import com.example.zhilu.domain.model.ReminderWithContext
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.domain.model.TodoWithContext
import com.example.zhilu.domain.repository.ReminderRepository
import com.example.zhilu.domain.repository.TodoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 提醒处置必须**回写源头**（既有缺陷的护栏）：
 * 提醒里点完成，笔记中的待办也要打勾；取消提醒要清掉待办上的提醒时间。
 * REVIEW 类由复习计划驱动，不做任何源头回写。
 */
class ResolveReminderUseCaseTest {
    private val now = 1_700_000_000_000L

    @Test
    fun `completing a todo reminder also completes the todo`() = runTest {
        val reminderRepository = RecordingReminderRepository()
        val todoRepository = RecordingTodoRepository()
        val useCase = ResolveReminderUseCase(reminderRepository, todoRepository)

        val result = useCase.complete(todoReminder(sourceId = 42L), now)

        assertEquals(RepositoryResult.Success(Unit), result)
        assertEquals(listOf(ReminderType.TODO to 42L), reminderRepository.doneCalls)
        assertEquals(listOf(42L to now), todoRepository.completedCalls)
    }

    @Test
    fun `completing a review reminder does not touch todos`() = runTest {
        val reminderRepository = RecordingReminderRepository()
        val todoRepository = RecordingTodoRepository()
        val useCase = ResolveReminderUseCase(reminderRepository, todoRepository)

        useCase.complete(reviewReminder(sourceId = 7L), now)

        assertEquals(listOf(ReminderType.REVIEW to 7L), reminderRepository.doneCalls)
        assertEquals(emptyList<Pair<Long, Long>>(), todoRepository.completedCalls)
    }

    @Test
    fun `failing reminder write skips the todo write-back`() = runTest {
        val reminderRepository = RecordingReminderRepository(
            doneResult = RepositoryResult.Error("提醒写入失败")
        )
        val todoRepository = RecordingTodoRepository()
        val useCase = ResolveReminderUseCase(reminderRepository, todoRepository)

        val result = useCase.complete(todoReminder(sourceId = 42L), now)

        assertEquals(RepositoryResult.Error("提醒写入失败"), result)
        assertEquals("提醒都没写成功，不该单独把待办打勾", emptyList<Pair<Long, Long>>(), todoRepository.completedCalls)
    }

    @Test
    fun `cancelling a todo reminder clears its remind time but keeps the todo`() = runTest {
        val reminderRepository = RecordingReminderRepository()
        val todoRepository = RecordingTodoRepository()
        val useCase = ResolveReminderUseCase(reminderRepository, todoRepository)

        useCase.cancel(todoReminder(sourceId = 43L), now)

        assertEquals(listOf(ReminderType.TODO to 43L), reminderRepository.cancelCalls)
        assertEquals("取消提醒只清提醒时间，不减待办", listOf(Triple(43L, null, now)), todoRepository.remindAtCalls)
    }

    @Test
    fun `snoozing rebuilds the reminder at the new time and moves the todo reminder`() = runTest {
        val reminderRepository = RecordingReminderRepository()
        val todoRepository = RecordingTodoRepository()
        val useCase = ResolveReminderUseCase(reminderRepository, todoRepository)
        val dueAt = now + 3_600_000L

        val result = useCase.snooze(todoReminder(sourceId = 44L), dueAt, now)

        assertEquals(RepositoryResult.Success(Unit), result)
        val scheduled = reminderRepository.scheduledCalls.single()
        assertEquals("延后是新增一条（id 归零），旧实例由仓库取消", 0L, scheduled.id)
        assertEquals(dueAt, scheduled.dueAt)
        assertEquals(44L, scheduled.sourceId)
        assertEquals(listOf(Triple(44L, dueAt, now)), todoRepository.remindAtCalls)
    }

    @Test
    fun `snoozing a review reminder only rebuilds the reminder`() = runTest {
        val reminderRepository = RecordingReminderRepository()
        val todoRepository = RecordingTodoRepository()
        val useCase = ResolveReminderUseCase(reminderRepository, todoRepository)

        useCase.snooze(reviewReminder(sourceId = 8L), now + 1, now)

        assertEquals(1, reminderRepository.scheduledCalls.size)
        assertEquals(emptyList<Triple<Long, Long?, Long>>(), todoRepository.remindAtCalls)
    }

    private fun todoReminder(sourceId: Long) = ReminderInstance(
        id = 100 + sourceId,
        type = ReminderType.TODO,
        sourceId = sourceId,
        noteId = 1L,
        dueAt = now,
        status = ReminderStatus.SCHEDULED,
        notificationId = sourceId.toInt(),
        updatedAt = sourceId
    )

    private fun reviewReminder(sourceId: Long) = ReminderInstance(
        id = 200 + sourceId,
        type = ReminderType.REVIEW,
        sourceId = sourceId,
        noteId = 2L,
        dueAt = now,
        status = ReminderStatus.SCHEDULED,
        notificationId = sourceId.toInt(),
        updatedAt = sourceId
    )
}

private class RecordingReminderRepository(
    private val doneResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
) : ReminderRepository {
    val doneCalls = mutableListOf<Pair<ReminderType, Long>>()
    val cancelCalls = mutableListOf<Pair<ReminderType, Long>>()
    val scheduledCalls = mutableListOf<ReminderInstance>()

    override fun observeAll(): Flow<RepositoryResult<List<ReminderInstance>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override fun observeAllWithContext(): Flow<RepositoryResult<List<ReminderWithContext>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun getDueReminders(now: Long): RepositoryResult<List<ReminderInstance>> =
        RepositoryResult.Success(emptyList())

    override suspend fun getActiveReminder(
        type: ReminderType,
        sourceId: Long
    ): RepositoryResult<ReminderInstance?> = RepositoryResult.Success(null)

    override suspend fun upsertScheduled(reminder: ReminderInstance): RepositoryResult<Long> {
        scheduledCalls += reminder
        return RepositoryResult.Success(reminder.id)
    }

    override suspend fun markDone(type: ReminderType, sourceId: Long): RepositoryResult<Unit> {
        doneCalls += type to sourceId
        return doneResult
    }

    override suspend fun markFired(
        reminder: ReminderInstance,
        firedAt: Long
    ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

    override suspend fun cancel(type: ReminderType, sourceId: Long): RepositoryResult<Unit> {
        cancelCalls += type to sourceId
        return RepositoryResult.Success(Unit)
    }
}

private class RecordingTodoRepository : TodoRepository {

    override fun observeAllWithContext(): Flow<RepositoryResult<List<TodoWithContext>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun deleteTodo(id: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    val completedCalls = mutableListOf<Pair<Long, Long>>()
    val remindAtCalls = mutableListOf<Triple<Long, Long?, Long>>()

    override fun observeByNoteId(noteId: Long): Flow<RepositoryResult<List<TodoItem>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun addTodo(todo: TodoItem): RepositoryResult<Long> =
        RepositoryResult.Success(1L)

    override suspend fun updateTodo(todo: TodoItem): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun completeTodo(id: Long, completedAt: Long): RepositoryResult<Unit> {
        completedCalls += id to completedAt
        return RepositoryResult.Success(Unit)
    }

    override suspend fun updateRemindAt(
        id: Long,
        remindAt: Long?,
        updatedAt: Long
    ): RepositoryResult<Unit> {
        remindAtCalls += Triple(id, remindAt, updatedAt)
        return RepositoryResult.Success(Unit)
    }
}
