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
     * FTS 版关键词搜索（检索主路径）。
     *
     * [ftsQuery] 必须是 `NoteSearchTokens.buildMatchQuery` 的产物（空格分隔的 bigram 流，
     * 隐式 AND 语义），不能传用户原始输入 —— 原始输入里的引号/星号/连字符会撞 FTS 查询语法，
     * 显式 `AND` 运算符在 Android 自带 SQLite 上也不被识别。
     *
     * FTS4 没有 `bm25()`，无法按相关性排序：候选本身就是 bigram AND 的精确命中，
     * 按 `updatedAt DESC` 排序是"同样命中时新内容优先"的合理降级（文档 §D3 预案）。
     * **必须有 LIMIT**：AI 工具路径的结果会整体进上下文预算，UI 路径要防
     * "高频字命中全库"把结果集撑爆（两种调用方各自的取值见 `NoteRepository`）。
     */
    @Query(
        """
        SELECT n.* FROM notes n
        WHERE n.deletedAt IS NULL
          AND n.id IN (SELECT rowid FROM note_fts WHERE note_fts MATCH :ftsQuery)
        ORDER BY n.updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchFts(ftsQuery: String, limit: Int): List<NoteEntity>

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

    /**
     * FTS 版的「关键词 × 标签集合」组合查询（检索主路径）。
     *
     * 与 [searchWithTags] 的差异只有关键词的匹配方式（FTS MATCH 替 LIKE）；
     * 标签侧仍是既有 AND 语义（命中标签数 = 选中总数）。
     * 纯标签浏览（关键词为空）不走这里 —— 调用方在 keyword 无 token 时可查时
     * 退回 [searchWithTags] 的 `:keyword = ''` 分支。
     */
    @Query(
        """
        SELECT n.* FROM notes n
        WHERE n.deletedAt IS NULL
          AND n.id IN (SELECT rowid FROM note_fts WHERE note_fts MATCH :ftsQuery)
          AND (
            SELECT COUNT(DISTINCT nt2.tagId) FROM note_tags nt2
            WHERE nt2.noteId = n.id AND nt2.tagId IN (:tagIds)
          ) = :tagCount
        ORDER BY n.updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchWithTagsFts(
        ftsQuery: String,
        tagIds: List<Long>,
        tagCount: Int,
        limit: Int
    ): List<NoteEntity>

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

    /** 含已软删笔记的全表行数 —— 检索索引（含软删行）的一致性基数，勿与 [count] 混用。 */
    @Query("SELECT COUNT(*) FROM notes")
    suspend fun countAll(): Int

    /** 全表 id（含软删）—— 检索索引全量重建的输入。 */
    @Query("SELECT id FROM notes")
    suspend fun getAllIds(): List<Long>
}
