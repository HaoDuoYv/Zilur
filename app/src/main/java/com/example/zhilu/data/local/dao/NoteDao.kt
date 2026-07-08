package com.example.zhilu.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.zhilu.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavorites(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getDeleted(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getByIdIncludingDeleted(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: Long): NoteEntity?

    @Query(
        """
        SELECT DISTINCT n.* FROM notes n
        LEFT JOIN note_blocks b ON n.id = b.noteId
        LEFT JOIN note_tags nt ON n.id = nt.noteId
        LEFT JOIN tags t ON nt.tagId = t.id
        WHERE n.deletedAt IS NULL
          AND (
            n.title LIKE '%' || :keyword || '%'
            OR b.content LIKE '%' || :keyword || '%'
            OR t.name LIKE '%' || :keyword || '%'
          )
        ORDER BY n.updatedAt DESC
        """
    )
    suspend fun search(keyword: String): List<NoteEntity>

    @Query(
        """
        SELECT n.* FROM notes n
        INNER JOIN note_tags nt ON n.id = nt.noteId
        WHERE n.deletedAt IS NULL AND nt.tagId = :tagId
        ORDER BY n.updatedAt DESC
        """
    )
    suspend fun getByTagId(tagId: Long): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("UPDATE notes SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)

    @Query("UPDATE notes SET deletedAt = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: Long, updatedAt: Long)

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL")
    suspend fun clearDeleted()

    @Query("SELECT COUNT(*) FROM notes WHERE deletedAt IS NULL")
    suspend fun count(): Int
}
