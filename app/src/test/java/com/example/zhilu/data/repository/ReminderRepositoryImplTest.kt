package com.example.zhilu.data.repository

import com.example.zhilu.data.local.dao.ReminderDao
import com.example.zhilu.data.local.entity.ReminderInstanceEntity
import com.example.zhilu.data.local.mapper.ReminderMapper
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderStatus
import com.example.zhilu.domain.model.ReminderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderRepositoryImplTest {
    @Test
    fun insertScheduledReminderCancelsPreviousActiveReminderForSameSource() = runTest {
        val dao = FakeReminderDao()
        val repository = ReminderRepositoryImpl(dao, NoOpRepositoryTransactionRunner)

        repository.upsertScheduled(
            ReminderInstance(
                type = ReminderType.REVIEW,
                sourceId = 8,
                noteId = 5,
                dueAt = 100,
                notificationId = 800
            )
        )
        repository.upsertScheduled(
            ReminderInstance(
                type = ReminderType.REVIEW,
                sourceId = 8,
                noteId = 5,
                dueAt = 200,
                notificationId = 801
            )
        )

        val active = dao.items.filter { it.status == ReminderStatus.SCHEDULED.value }
        assertEquals(1, active.size)
        assertEquals(200L, active.single().dueAt)
    }

    @Test
    fun upsertScheduledRunsCancelAndInsertInTransaction() = runTest {
        val dao = FakeReminderDao()
        val transactionRunner = TrackingTransactionRunner()
        val repository = ReminderRepositoryImpl(dao, transactionRunner)

        repository.upsertScheduled(reminder(type = ReminderType.REVIEW, sourceId = 8, dueAt = 100))

        assertEquals(1, transactionRunner.callCount)
    }

    @Test
    fun upsertScheduledSchedulesOneTimeReminderCheckAfterSuccessfulSave() = runTest {
        val dao = FakeReminderDao()
        val scheduler = RecordingReminderCheckScheduler()
        val repository = ReminderRepositoryImpl(dao, NoOpRepositoryTransactionRunner, scheduler)

        repository.upsertScheduled(reminder(type = ReminderType.REVIEW, sourceId = 8, dueAt = 100))

        assertEquals(1, scheduler.oneTimeCheckCount)
    }

    @Test
    fun markDoneMarksActiveRemindersForSourceDone() = runTest {
        val dao = FakeReminderDao()
        val repository = ReminderRepositoryImpl(dao, NoOpRepositoryTransactionRunner)
        repository.upsertScheduled(reminder(type = ReminderType.TODO, sourceId = 4, dueAt = 100))

        repository.markDone(type = ReminderType.TODO, sourceId = 4)

        assertEquals(ReminderStatus.DONE.value, dao.items.single().status)
    }

    @Test
    fun markFiredUpdatesReminderStatusAndFiredAt() = runTest {
        val dao = FakeReminderDao()
        val repository = ReminderRepositoryImpl(dao, NoOpRepositoryTransactionRunner)
        val insertResult = repository.upsertScheduled(reminder(type = ReminderType.REVIEW, sourceId = 9, dueAt = 100))
        val id = (insertResult as RepositoryResult.Success).data
        val scheduled = dao.items.single { it.id == id }

        repository.markFired(
            reminder = ReminderMapper.toDomain(scheduled),
            firedAt = 300
        )

        val fired = dao.items.single { it.id == id }
        assertEquals(ReminderStatus.FIRED.value, fired.status)
        assertEquals(300L, fired.firedAt)
        assertEquals(300L, fired.updatedAt)
    }

    @Test
    fun markFiredDoesNotReviveCanceledReminderFromStaleSnapshot() = runTest {
        val dao = FakeReminderDao()
        val repository = ReminderRepositoryImpl(dao, NoOpRepositoryTransactionRunner)
        val insertResult = repository.upsertScheduled(reminder(type = ReminderType.REVIEW, sourceId = 12, dueAt = 100))
        val id = (insertResult as RepositoryResult.Success).data
        val staleScheduled = ReminderMapper.toDomain(dao.items.single { it.id == id })
        repository.cancel(type = ReminderType.REVIEW, sourceId = 12)

        repository.markFired(reminder = staleScheduled, firedAt = 300)

        val reminder = dao.items.single { it.id == id }
        assertEquals(ReminderStatus.CANCELED.value, reminder.status)
        assertEquals(null, reminder.firedAt)
    }

    @Test
    fun markFiredDoesNotReviveDoneReminderFromStaleSnapshot() = runTest {
        val dao = FakeReminderDao()
        val repository = ReminderRepositoryImpl(dao, NoOpRepositoryTransactionRunner)
        val insertResult = repository.upsertScheduled(reminder(type = ReminderType.REVIEW, sourceId = 13, dueAt = 100))
        val id = (insertResult as RepositoryResult.Success).data
        val staleScheduled = ReminderMapper.toDomain(dao.items.single { it.id == id })
        repository.markDone(type = ReminderType.REVIEW, sourceId = 13)

        repository.markFired(reminder = staleScheduled, firedAt = 300)

        val reminder = dao.items.single { it.id == id }
        assertEquals(ReminderStatus.DONE.value, reminder.status)
        assertEquals(null, reminder.firedAt)
    }

    @Test
    fun cancelMarksActiveRemindersForSourceCanceled() = runTest {
        val dao = FakeReminderDao()
        val repository = ReminderRepositoryImpl(dao, NoOpRepositoryTransactionRunner)
        repository.upsertScheduled(reminder(type = ReminderType.REVIEW, sourceId = 11, dueAt = 100))

        repository.cancel(type = ReminderType.REVIEW, sourceId = 11)

        assertEquals(ReminderStatus.CANCELED.value, dao.items.single().status)
    }

    private fun reminder(
        type: ReminderType,
        sourceId: Long,
        dueAt: Long
    ): ReminderInstance = ReminderInstance(
        type = type,
        sourceId = sourceId,
        noteId = 5,
        dueAt = dueAt,
        notificationId = dueAt.toInt()
    )
}

private class FakeReminderDao : ReminderDao {
    val items = mutableListOf<ReminderInstanceEntity>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<ReminderInstanceEntity>> = flowOf(items)

    override suspend fun dueReminders(
        now: Long,
        statuses: List<Int>
    ): List<ReminderInstanceEntity> = items
        .filter { it.dueAt <= now && it.status in statuses }
        .sortedBy { it.dueAt }

    override suspend fun activeForSource(
        type: Int,
        sourceId: Long,
        activeStatuses: List<Int>
    ): ReminderInstanceEntity? = items
        .filter { it.type == type && it.sourceId == sourceId && it.status in activeStatuses }
        .maxByOrNull { it.updatedAt }

    override suspend fun insert(reminder: ReminderInstanceEntity): Long {
        val id = if (reminder.id == 0L) nextId++ else reminder.id
        val stored = reminder.copy(id = id)
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) {
            items[index] = stored
        } else {
            items += stored
        }
        return id
    }

    override suspend fun update(reminder: ReminderInstanceEntity) {
        val index = items.indexOfFirst { it.id == reminder.id }
        if (index >= 0) {
            items[index] = reminder
        }
    }

    override suspend fun markFiredIfScheduled(
        id: Long,
        type: Int,
        sourceId: Long,
        firedStatus: Int,
        firedAt: Long,
        updatedAt: Long,
        scheduledStatus: Int
    ): Int {
        val index = items.indexOfFirst {
            it.id == id &&
                it.type == type &&
                it.sourceId == sourceId &&
                it.status == scheduledStatus
        }
        if (index < 0) return 0
        items[index] = items[index].copy(
            status = firedStatus,
            firedAt = firedAt,
            updatedAt = updatedAt
        )
        return 1
    }

    override suspend fun markActiveForSource(
        type: Int,
        sourceId: Long,
        status: Int,
        updatedAt: Long,
        activeStatuses: List<Int>
    ) {
        items.replaceAll { item ->
            if (item.type == type && item.sourceId == sourceId && item.status in activeStatuses) {
                item.copy(status = status, updatedAt = updatedAt)
            } else {
                item
            }
        }
    }
}

private class TrackingTransactionRunner : RepositoryTransactionRunner {
    var callCount = 0

    override suspend fun <T> runInTransaction(block: suspend () -> T): T {
        callCount++
        return block()
    }
}

private class RecordingReminderCheckScheduler : ReminderRepositoryImpl.ReminderCheckScheduler {
    var oneTimeCheckCount = 0

    override fun scheduleOneTimeCheck() {
        oneTimeCheckCount++
    }
}
