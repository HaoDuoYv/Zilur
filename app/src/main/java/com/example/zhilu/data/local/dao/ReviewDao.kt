package com.example.zhilu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.zhilu.data.local.entity.ReviewDailyCountRow
import com.example.zhilu.data.local.entity.ReviewEventEntity
import com.example.zhilu.data.local.entity.ReviewPlanEntity
import com.example.zhilu.data.local.entity.ReviewPlanRow
import com.example.zhilu.data.local.entity.ReviewRatingCountRow
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

    /**
     * 窗口内每日复习次数，按天分桶（单条 `GROUP BY`）。
     *
     * 分桶基准是调用方给的 `windowStart`，用整数除法落格：`(reviewedAt - windowStart) / 一天`。
     * 这样"哪一天"由界面口径（今天 0 点）决定，SQL 里不出现时区换算。
     * 「今天复习了几篇」就是最后一格，不再另设一条计数查询。
     */
    @Query(
        """
        SELECT (reviewedAt - :windowStart) / 86400000 AS dayIndex, COUNT(*) AS eventCount
        FROM review_events
        WHERE reviewedAt >= :windowStart
        GROUP BY dayIndex
        """
    )
    fun observeDailyCountsSince(windowStart: Long): Flow<List<ReviewDailyCountRow>>

    /** 窗口内的评价分布（困难 / 正常 / 已掌握各多少次）。 */
    @Query(
        """
        SELECT rating, COUNT(*) AS eventCount
        FROM review_events
        WHERE reviewedAt >= :windowStart
        GROUP BY rating
        """
    )
    fun observeRatingCountsSince(windowStart: Long): Flow<List<ReviewRatingCountRow>>

    @Query("SELECT * FROM review_plans WHERE noteId = :noteId LIMIT 1")
    suspend fun getPlanByNoteId(noteId: Long): ReviewPlanEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlan(plan: ReviewPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: ReviewPlanEntity)

    @Insert
    suspend fun insertEvent(event: ReviewEventEntity): Long
}
