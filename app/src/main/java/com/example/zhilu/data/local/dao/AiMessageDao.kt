package com.example.zhilu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.zhilu.data.local.entity.AiMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiMessageDao {
    @Query("SELECT * FROM ai_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun getByConversation(conversationId: Long): Flow<List<AiMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: AiMessageEntity): Long

    @Query("DELETE FROM ai_messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: Long)
}
