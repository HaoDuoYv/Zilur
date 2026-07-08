package com.example.zhilu.domain.repository

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderType
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun observeAll(): Flow<RepositoryResult<List<ReminderInstance>>>
    suspend fun getDueReminders(now: Long): RepositoryResult<List<ReminderInstance>>
    suspend fun getActiveReminder(type: ReminderType, sourceId: Long): RepositoryResult<ReminderInstance?>
    suspend fun upsertScheduled(reminder: ReminderInstance): RepositoryResult<Long>
    suspend fun markDone(type: ReminderType, sourceId: Long): RepositoryResult<Unit>
    suspend fun markFired(reminder: ReminderInstance, firedAt: Long): RepositoryResult<Unit>
    suspend fun cancel(type: ReminderType, sourceId: Long): RepositoryResult<Unit>
}
