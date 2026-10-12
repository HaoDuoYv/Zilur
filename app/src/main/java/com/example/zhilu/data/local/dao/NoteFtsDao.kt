package com.example.zhilu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.zhilu.data.local.entity.NoteFtsEntity

/**
 * 笔记检索索引（FTS4）的读写。
 *
 * FTS3/4 的 `INSERT OR REPLACE` 有"幽灵 token"坑（替换后旧 token 仍可命中），
 * 所以这里**只提供**显式的"先删后插"接口，重建逻辑统一收敛在
 * `com.example.zhilu.data.repository.NoteSearchIndexer`。
 */
@Dao
interface NoteFtsDao {

    @Insert
    suspend fun insert(entity: NoteFtsEntity)

    @Query("DELETE FROM note_fts WHERE rowid = :rowId")
    suspend fun delete(rowId: Long)

    @Query("DELETE FROM note_fts")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM note_fts")
    suspend fun count(): Int

    /** 清掉已不存在的笔记留下的索引行（`clearDeletedNotes` 后与启动兜底用）。 */
    @Query("DELETE FROM note_fts WHERE rowid NOT IN (SELECT id FROM notes)")
    suspend fun pruneOrphans()
}
