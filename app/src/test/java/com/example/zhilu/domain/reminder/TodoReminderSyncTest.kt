package com.example.zhilu.domain.reminder

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderType
import com.example.zhilu.domain.model.ReminderWithContext
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 待办 → TODO 提醒的同步点。
 *
 * 重点不是"三个分支各自调对了方法"，而是 **通知 id 的派生只有这一份**：
 * 笔记页与 AI 的 `add_todos` 都从这里取，谁改时间都不会在通知栏多留一条。
 */
class TodoReminderSyncTest {

    @Test
    fun `schedule upserts with a todo typed reminder`() = runTest {
        val reminders = RecordingReminderRepository()
        val sync = TodoReminderSync(reminders)

        val result = sync.schedule(todoId = 7, noteId = 3, dueAt = 5_000L)

        assertTrue(result is RepositoryResult.Success)
        val scheduled = reminders.upserted.single()
        assertEquals(ReminderType.TODO, scheduled.type)
        assertEquals(7L, scheduled.sourceId)
        assertEquals(3L, scheduled.noteId)
        assertEquals(5_000L, scheduled.dueAt)
        assertEquals(TodoReminderSync.notificationIdFor(7), scheduled.notificationId)
    }

    @Test
    fun `notification id is stable for the same todo`() {
        // 同一条待办改了提醒时间，通知必须还是同一条 —— 否则通知栏会堆出两条
        assertEquals(
            TodoReminderSync.notificationIdFor(42),
            TodoReminderSync.notificationIdFor(42)
        )
        assertTrue(TodoReminderSync.notificationIdFor(42) != TodoReminderSync.notificationIdFor(43))
    }

    @Test
    fun `reconcile on a completed todo ends the reminder`() = runTest {
        val reminders = RecordingReminderRepository()
        val sync = TodoReminderSync(reminders)

        sync.reconcile(TodoItem(id = 9, noteId = 1, content = "已完成的", completedAt = 100L))

        assertEquals(listOf(9L), reminders.markedDone)
        assertTrue(reminders.upserted.isEmpty())
        assertTrue(reminders.cancelled.isEmpty())
    }

    @Test
    fun `reconcile with a remindAt rebuilds the reminder`() = runTest {
        val reminders = RecordingReminderRepository()
        val sync = TodoReminderSync(reminders)

        sync.reconcile(TodoItem(id = 9, noteId = 1, content = "带提醒", remindAt = 4_000L))

        val scheduled = reminders.upserted.single()
        assertEquals(9L, scheduled.sourceId)
        assertEquals(4_000L, scheduled.dueAt)
    }

    @Test
    fun `reconcile without a remindAt cancels it`() = runTest {
        val reminders = RecordingReminderRepository()
        val sync = TodoReminderSync(reminders)

        sync.reconcile(TodoItem(id = 9, noteId = 1, content = "清掉了提醒"))

        assertEquals(listOf(9L to ReminderType.TODO), reminders.cancelled)
    }

    @Test
    fun `reconcile falls back to the caller note when the todo has none`() = runTest {
        val reminders = RecordingReminderRepository()
        val sync = TodoReminderSync(reminders)

        // AI 建的待办总是带 noteId，但模型也可能漏 —— 兜底用调用方的笔记
        sync.reconcile(
            TodoItem(id = 9, noteId = null, content = "无归属", remindAt = 4_000L),
            fallbackNoteId = 55L
        )

        assertEquals(55L, reminders.upserted.single().noteId)
    }

    @Test
    fun `failures are reported instead of swallowed`() = runTest {
        val reminders = RecordingReminderRepository(failWrites = true)
        val sync = TodoReminderSync(reminders)

        assertTrue(sync.schedule(1, 2, 3) is RepositoryResult.Error)
        assertTrue(sync.cancel(1) is RepositoryResult.Error)
        assertTrue(sync.markDone(1) is RepositoryResult.Error)
    }
}

private class RecordingReminderRepository(
    private val failWrites: Boolean = false
) : ReminderRepository {
    val upserted = mutableListOf<ReminderInstance>()
    val cancelled = mutableListOf<Pair<Long, ReminderType>>()
    val markedDone = mutableListOf<Long>()

    private fun failure() = RepositoryResult.Error("boom", IllegalStateException("boom"))

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
        if (failWrites) return failure()
        upserted += reminder
        return RepositoryResult.Success(reminder.sourceId)
    }

    override suspend fun markDone(type: ReminderType, sourceId: Long): RepositoryResult<Unit> {
        if (failWrites) return failure()
        markedDone += sourceId
        return RepositoryResult.Success(Unit)
    }

    override suspend fun markFired(
        reminder: ReminderInstance,
        firedAt: Long
    ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

    override suspend fun cancel(type: ReminderType, sourceId: Long): RepositoryResult<Unit> {
        if (failWrites) return failure()
        cancelled += sourceId to type
        return RepositoryResult.Success(Unit)
    }
}
