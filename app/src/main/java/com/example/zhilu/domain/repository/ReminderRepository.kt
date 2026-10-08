package com.example.zhilu.domain.repository

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderType
import com.example.zhilu.domain.model.ReminderWithContext
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun observeAll(): Flow<RepositoryResult<List<ReminderInstance>>>

    /** 带展示上下文的提醒流（笔记标题 / 待办文本 / 复习档位），复习中心「提醒」档用。 */
    fun observeAllWithContext(): Flow<RepositoryResult<List<ReminderWithContext>>>
    suspend fun getDueReminders(now: Long): RepositoryResult<List<ReminderInstance>>
    suspend fun getActiveReminder(type: ReminderType, sourceId: Long): RepositoryResult<ReminderInstance?>
    suspend fun upsertScheduled(reminder: ReminderInstance): RepositoryResult<Long>
    suspend fun markDone(type: ReminderType, sourceId: Long): RepositoryResult<Unit>
    suspend fun markFired(reminder: ReminderInstance, firedAt: Long): RepositoryResult<Unit>
    suspend fun cancel(type: ReminderType, sourceId: Long): RepositoryResult<Unit>
}
