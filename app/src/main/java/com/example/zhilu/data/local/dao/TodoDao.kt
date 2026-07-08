package com.example.zhilu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.zhilu.data.local.entity.TodoItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_items WHERE noteId = :noteId ORDER BY sortOrder, createdAt")
    fun observeByNoteId(noteId: Long): Flow<List<TodoItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: TodoItemEntity): Long

    @Update
    suspend fun update(todo: TodoItemEntity)

    @Query("SELECT * FROM todo_items WHERE id = :id")
    suspend fun getById(id: Long): TodoItemEntity?
}
