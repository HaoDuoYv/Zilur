package com.example.zhilu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.zhilu.data.local.entity.ReviewEventEntity
import com.example.zhilu.data.local.entity.ReviewPlanEntity

@Dao
interface ReviewDao {
    @Query("SELECT * FROM review_plans WHERE noteId = :noteId LIMIT 1")
    suspend fun getPlanByNoteId(noteId: Long): ReviewPlanEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlan(plan: ReviewPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: ReviewPlanEntity)

    @Insert
    suspend fun insertEvent(event: ReviewEventEntity): Long
}
