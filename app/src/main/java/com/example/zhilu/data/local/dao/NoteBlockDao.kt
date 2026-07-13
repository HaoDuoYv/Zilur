package com.example.zhilu.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.zhilu.data.local.entity.NoteBlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteBlockDao {
    @Query("SELECT * FROM note_blocks WHERE noteId = :noteId ORDER BY sortOrder")
    fun getByNoteId(noteId: Long): Flow<List<NoteBlockEntity>>

    @Query("SELECT * FROM note_blocks WHERE noteId = :noteId ORDER BY sortOrder")
    suspend fun getByNoteIdOnce(noteId: Long): List<NoteBlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(block: NoteBlockEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(blocks: List<NoteBlockEntity>): List<Long>

    @Update
    suspend fun update(block: NoteBlockEntity)

    @Delete
    suspend fun delete(block: NoteBlockEntity)

    @Query("DELETE FROM note_blocks WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: Long)

    @Query("SELECT COUNT(*) FROM note_blocks WHERE noteId = :noteId")
    suspend fun countByNoteId(noteId: Long): Int
}
