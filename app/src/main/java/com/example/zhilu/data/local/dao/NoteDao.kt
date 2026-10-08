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

    /**
     * 关键词 × 标签集合的组合查询（搜索页筛选条用）。
     *
     * - 关键词为空串时退化为"纯标签浏览"（即老标签页「点标签看笔记」的职责）；
     * - 多标签为 **AND** 语义：要求笔记命中的选中标签数 = 选中总数；
     * - [tagIds] **必须非空**——Room 对空集合会生成非法的 `IN ()`（SQLite 语法错误），
     *   无标签筛选时请走 [search]。
     */
    @Query(
        """
        SELECT n.* FROM notes n
        WHERE n.deletedAt IS NULL
          AND (
            :keyword = ''
            OR n.title LIKE '%' || :keyword || '%'
            OR EXISTS (
                SELECT 1 FROM note_blocks b
                WHERE b.noteId = n.id AND b.content LIKE '%' || :keyword || '%'
            )
            OR EXISTS (
                SELECT 1 FROM note_tags nt
                INNER JOIN tags t ON t.id = nt.tagId
                WHERE nt.noteId = n.id AND t.name LIKE '%' || :keyword || '%'
            )
          )
          AND (
            SELECT COUNT(DISTINCT nt2.tagId) FROM note_tags nt2
            WHERE nt2.noteId = n.id AND nt2.tagId IN (:tagIds)
          ) = :tagCount
        ORDER BY n.updatedAt DESC
        """
    )
    suspend fun searchWithTags(keyword: String, tagIds: List<Long>, tagCount: Int): List<NoteEntity>

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
