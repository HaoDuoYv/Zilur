package com.example.zhilu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.zhilu.data.local.entity.TodoItemEntity
import com.example.zhilu.data.local.entity.TodoWithContextRow
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_items WHERE noteId = :noteId ORDER BY sortOrder, createdAt")
    fun observeByNoteId(noteId: Long): Flow<List<TodoItemEntity>>

    /**
     * 跨笔记的全部待办（全局「待办」档），带来源笔记标题。
     *
     * 排序由 SQL 一次定好：未完成在前（`completedAt IS NOT NULL` = 0）；
     * 未完成组里"带提醒的按时间先后"排在"没有提醒的"前面，同组按创建时间稳定排序。
     * 已完成组的"最近完成在前"由 ViewModel 在小组内倒序（分组键不同，SQL 里硬塞会更绕）。
     *
     * JOIN 带 `deletedAt IS NULL`：回收站里的笔记不算"还活着"，
     * [TodoWithContextRow.noteAlive] 据此关闭行内的"点进笔记"入口。
     */
    @Query(
        """
        SELECT t.*, n.title AS noteTitle, (n.id IS NOT NULL) AS noteAlive
        FROM todo_items t
        LEFT JOIN notes n ON n.id = t.noteId AND n.deletedAt IS NULL
        ORDER BY (t.completedAt IS NOT NULL), (t.remindAt IS NULL), t.remindAt, t.createdAt
        """
    )
    fun observeAllWithContext(): Flow<List<TodoWithContextRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: TodoItemEntity): Long

    @Update
    suspend fun update(todo: TodoItemEntity)

    @Query("SELECT * FROM todo_items WHERE id = :id")
    suspend fun getById(id: Long): TodoItemEntity?

    /** 删除待办本体；TODO 提醒实例的清理由调用方经 `TodoReminderSync.cancel` 完成。 */
    @Query("DELETE FROM todo_items WHERE id = :id")
    suspend fun delete(id: Long)
}
