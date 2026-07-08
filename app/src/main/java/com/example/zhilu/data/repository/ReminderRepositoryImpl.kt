package com.example.zhilu.data.repository

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.local.dao.ReminderDao
import com.example.zhilu.data.local.mapper.ReminderMapper
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderStatus
import com.example.zhilu.domain.model.ReminderType
import com.example.zhilu.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class ReminderRepositoryImpl(
    private val reminderDao: ReminderDao,
    private val transactionRunner: RepositoryTransactionRunner,
    private val reminderCheckScheduler: ReminderCheckScheduler = ReminderCheckScheduler.NoOp
) : ReminderRepository {
    override fun observeAll(): Flow<RepositoryResult<List<ReminderInstance>>> = reminderDao.observeAll()
        .map { entities -> RepositoryResult.Success(entities.map(ReminderMapper::toDomain)) as RepositoryResult<List<ReminderInstance>> }
        .catch { e -> emit(RepositoryResult.Error("Failed to load reminders", e)) }

    override suspend fun getDueReminders(now: Long): RepositoryResult<List<ReminderInstance>> = runCatching {
        reminderDao.dueReminders(
            now = now,
            statuses = listOf(ReminderStatus.SCHEDULED.value)
        ).map(ReminderMapper::toDomain)
    }.toRepositoryResult("Failed to load due reminders")

    override suspend fun getActiveReminder(
        type: ReminderType,
        sourceId: Long
    ): RepositoryResult<ReminderInstance?> = runCatching {
        reminderDao.activeForSource(
            type = type.value,
            sourceId = sourceId,
            activeStatuses = activeStatuses
        )?.let(ReminderMapper::toDomain)
    }.toRepositoryResult("Failed to load active reminder")

    override suspend fun upsertScheduled(reminder: ReminderInstance): RepositoryResult<Long> {
        val result = runCatching {
            val now = System.currentTimeMillis()
            transactionRunner.runInTransaction {
                reminderDao.markActiveForSource(
                    type = reminder.type.value,
                    sourceId = reminder.sourceId,
                    status = ReminderStatus.CANCELED.value,
                    updatedAt = now,
                    activeStatuses = activeStatuses
                )
                reminderDao.insert(
                    ReminderMapper.toEntity(
                        reminder.copy(
                            status = ReminderStatus.SCHEDULED,
                            updatedAt = now,
                            firedAt = null
                        )
                    )
                )
            }
        }.toRepositoryResult("Failed to save reminder")

        if (result is RepositoryResult.Success) {
            reminderCheckScheduler.scheduleOneTimeCheck()
        }

        return result
    }

    override suspend fun markDone(type: ReminderType, sourceId: Long): RepositoryResult<Unit> = runCatching {
        reminderDao.markActiveForSource(
            type = type.value,
            sourceId = sourceId,
            status = ReminderStatus.DONE.value,
            updatedAt = System.currentTimeMillis(),
            activeStatuses = activeStatuses
        )
    }.toRepositoryResult("Failed to mark reminder done")

    override suspend fun markFired(
        reminder: ReminderInstance,
        firedAt: Long
    ): RepositoryResult<Unit> = runCatching {
        reminderDao.markFiredIfScheduled(
            id = reminder.id,
            type = reminder.type.value,
            sourceId = reminder.sourceId,
            firedStatus = ReminderStatus.FIRED.value,
            firedAt = firedAt,
            updatedAt = firedAt,
            scheduledStatus = ReminderStatus.SCHEDULED.value
        )
        Unit
    }.toRepositoryResult("Failed to mark reminder fired")

    override suspend fun cancel(type: ReminderType, sourceId: Long): RepositoryResult<Unit> = runCatching {
        reminderDao.markActiveForSource(
            type = type.value,
            sourceId = sourceId,
            status = ReminderStatus.CANCELED.value,
            updatedAt = System.currentTimeMillis(),
            activeStatuses = activeStatuses
        )
    }.toRepositoryResult("Failed to cancel reminder")

    fun interface ReminderCheckScheduler {
        fun scheduleOneTimeCheck()

        companion object {
            val NoOp = ReminderCheckScheduler {}
        }
    }

    private companion object {
        val activeStatuses = listOf(ReminderStatus.SCHEDULED.value, ReminderStatus.FIRED.value)
    }
}
