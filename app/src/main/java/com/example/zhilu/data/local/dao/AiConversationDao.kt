package com.example.zhilu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.zhilu.data.local.entity.AiConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiConversationDao {
    @Query("SELECT * FROM ai_conversations ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<AiConversationEntity>>

    @Query("SELECT * FROM ai_conversations WHERE id = :id")
    suspend fun getById(id: Long): AiConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: AiConversationEntity): Long

    @Query("UPDATE ai_conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String, updatedAt: Long)

    @Query("UPDATE ai_conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long)

    @Query("DELETE FROM ai_conversations WHERE id = :id")
    suspend fun deleteById(id: Long)
}
