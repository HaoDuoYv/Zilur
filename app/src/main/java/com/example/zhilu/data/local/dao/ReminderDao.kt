package com.example.zhilu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.zhilu.data.local.entity.ReminderInstanceEntity
import com.example.zhilu.data.local.entity.ReminderWithContextRow
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder_instances ORDER BY dueAt")
    fun observeAll(): Flow<List<ReminderInstanceEntity>>

    /**
     * 带展示上下文的提醒列表（笔记标题 / 待办文本 / 复习档位）。
     *
     * 两个 JOIN 必须带 `type` 条件——TODO 提醒的 sourceId 是 `todo_items.id`、
     * REVIEW 提醒的 sourceId 是 `review_plans.id`，不区分类型会串行。
     * 参数由仓库层传 `ReminderType.*.value`。
     */
    @Query(
        """
        SELECT r.*, n.title AS noteTitle, t.content AS todoContent, p.currentStep AS reviewStep
        FROM reminder_instances r
        LEFT JOIN notes n ON n.id = r.noteId
        LEFT JOIN todo_items t ON r.type = :todoType AND t.id = r.sourceId
        LEFT JOIN review_plans p ON r.type = :reviewType AND p.id = r.sourceId
        ORDER BY r.dueAt
        """
    )
    fun observeAllWithContext(reviewType: Int, todoType: Int): Flow<List<ReminderWithContextRow>>

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
