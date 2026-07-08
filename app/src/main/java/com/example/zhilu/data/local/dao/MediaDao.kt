package com.example.zhilu.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.zhilu.data.local.entity.MediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media ORDER BY createdAt DESC")
    suspend fun getAll(): List<MediaEntity>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getById(id: Long): MediaEntity?

    @Query("SELECT * FROM media WHERE uri = :uri")
    suspend fun getByUri(uri: String): MediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: MediaEntity): Long

    @Update
    suspend fun update(media: MediaEntity)

    @Delete
    suspend fun delete(media: MediaEntity)

    @Query("SELECT COUNT(*) FROM media")
    suspend fun count(): Long

    @Query("SELECT COUNT(*) FROM media")
    fun countFlow(): Flow<Int>

    @Query("SELECT COALESCE(SUM(size), 0) FROM media")
    suspend fun totalSize(): Long
}
