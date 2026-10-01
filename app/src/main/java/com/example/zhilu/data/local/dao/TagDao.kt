package com.example.zhilu.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.zhilu.data.local.entity.NoteTagEntity
import com.example.zhilu.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

/**
 * [TagDao.countNotesPerTag] 的投影结果：某个标签下有多少条**未删除**笔记。
 *
 * 它只为这一条聚合查询而存在，不对应任何表，所以不放进 `entity` 包。
 */
data class TagNoteCount(
    val tagId: Long,
    val noteCount: Int
)

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name")
    fun getAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: Long): TagEntity?

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun getByName(name: String): TagEntity?

    @Query("SELECT t.* FROM tags t JOIN note_tags nt ON t.id = nt.tagId WHERE nt.noteId = :noteId ORDER BY t.name")
    suspend fun getByNoteId(noteId: Long): List<TagEntity>

    /**
     * 按标签聚合笔记数，供标签索引行显示二级信息。
     *
     * 在 SQL 里 `GROUP BY` 一次算完，避免为了计数把每条笔记（连同卡片、区块、标签）都 hydrate 一遍。
     * 软删过滤与 `NoteDao.getAll` 保持一致：只统计 `deletedAt IS NULL` 的笔记。
     *
     * 注意 `note_tags` 是 `(noteId, tagId)` 联合主键，同一对不会重复，因此 `COUNT(*)` 即「笔记条数」。
     * 查询同时引用了 `note_tags` 与 `notes`，Room 会对两张表都注册观察，所以
     * 改关联、软删笔记、恢复笔记都会触发重新发射。
     */
    @Query(
        """
        SELECT nt.tagId AS tagId, COUNT(*) AS noteCount
        FROM note_tags nt
        JOIN notes n ON n.id = nt.noteId
        WHERE n.deletedAt IS NULL
        GROUP BY nt.tagId
        """
    )
    fun countNotesPerTag(): Flow<List<TagNoteCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Delete
    suspend fun delete(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNoteTag(noteTag: NoteTagEntity)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun deleteNoteTagsByNoteId(noteId: Long)

    @Query("SELECT COUNT(*) FROM tags")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM tags")
    fun countFlow(): Flow<Int>
}
