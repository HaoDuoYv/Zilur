package com.example.zhilu.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.zhilu.data.local.entity.NoteCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteCardDao {
    @Query("SELECT * FROM note_cards WHERE noteId = :noteId ORDER BY sortOrder")
    fun getByNoteId(noteId: Long): Flow<List<NoteCardEntity>>

    @Query("SELECT * FROM note_cards WHERE noteId = :noteId ORDER BY sortOrder")
    suspend fun getByNoteIdOnce(noteId: Long): List<NoteCardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: NoteCardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<NoteCardEntity>): List<Long>

    @Update
    suspend fun update(card: NoteCardEntity)

    @Delete
    suspend fun delete(card: NoteCardEntity)

    @Query("DELETE FROM note_cards WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: Long)

    @Query("SELECT COUNT(*) FROM note_cards WHERE noteId = :noteId")
    suspend fun countByNoteId(noteId: Long): Int
}
