package com.example.zhilu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.zhilu.data.local.entity.ReviewEventEntity
import com.example.zhilu.data.local.entity.ReviewPlanEntity
import com.example.zhilu.data.local.entity.ReviewPlanRow
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {
    /** 复习中心的计划列表（含笔记标题；回收站里的笔记不参与）。 */
    @Query(
        """
        SELECT p.*, n.title AS noteTitle
        FROM review_plans p
        INNER JOIN notes n ON n.id = p.noteId
        WHERE n.deletedAt IS NULL
        ORDER BY p.nextReviewAt
        """
    )
    fun observePlansWithNote(): Flow<List<ReviewPlanRow>>

    /** 统计某时刻（今天 0 点）之后的复习次数，供「今日已复习 N 篇」——下推 DAO，不在内存聚合。 */
    @Query("SELECT COUNT(*) FROM review_events WHERE reviewedAt >= :since")
    fun countEventsSince(since: Long): Flow<Int>

    @Query("SELECT * FROM review_plans WHERE noteId = :noteId LIMIT 1")
    suspend fun getPlanByNoteId(noteId: Long): ReviewPlanEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlan(plan: ReviewPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: ReviewPlanEntity)

    @Insert
    suspend fun insertEvent(event: ReviewEventEntity): Long
}
