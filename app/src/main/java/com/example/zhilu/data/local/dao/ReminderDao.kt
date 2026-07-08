package com.example.zhilu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.zhilu.data.local.entity.ReminderInstanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder_instances ORDER BY dueAt")
    fun observeAll(): Flow<List<ReminderInstanceEntity>>

    @Query("SELECT * FROM reminder_instances WHERE dueAt <= :now AND status IN (:statuses) ORDER BY dueAt")
    suspend fun dueReminders(now: Long, statuses: List<Int>): List<ReminderInstanceEntity>

    @Query("SELECT * FROM reminder_instances WHERE type = :type AND sourceId = :sourceId AND status IN (:activeStatuses) ORDER BY updatedAt DESC LIMIT 1")
    suspend fun activeForSource(
        type: Int,
        sourceId: Long,
        activeStatuses: List<Int>
    ): ReminderInstanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderInstanceEntity): Long

    @Update
    suspend fun update(reminder: ReminderInstanceEntity)

    @Query(
        """
        UPDATE reminder_instances
        SET status = :firedStatus, firedAt = :firedAt, updatedAt = :updatedAt
        WHERE id = :id AND type = :type AND sourceId = :sourceId AND status = :scheduledStatus
        """
    )
    suspend fun markFiredIfScheduled(
        id: Long,
        type: Int,
        sourceId: Long,
        firedStatus: Int,
        firedAt: Long,
        updatedAt: Long,
        scheduledStatus: Int
    ): Int

    @Query("UPDATE reminder_instances SET status = :status, updatedAt = :updatedAt WHERE type = :type AND sourceId = :sourceId AND status IN (:activeStatuses)")
    suspend fun markActiveForSource(
        type: Int,
        sourceId: Long,
        status: Int,
        updatedAt: Long,
        activeStatuses: List<Int>
    )
}
