package com.example.zhilu.domain.repository

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.domain.model.TodoWithContext
import kotlinx.coroutines.flow.Flow

interface TodoRepository {
    fun observeByNoteId(noteId: Long): Flow<RepositoryResult<List<TodoItem>>>

    /** 跨笔记的全部待办（全局「待办」档），带来源笔记标题，按"未完成 → 带提醒 → 时间"排序。 */
    fun observeAllWithContext(): Flow<RepositoryResult<List<TodoWithContext>>>

    suspend fun addTodo(todo: TodoItem): RepositoryResult<Long>
    suspend fun updateTodo(todo: TodoItem): RepositoryResult<Unit>
    suspend fun completeTodo(id: Long, completedAt: Long): RepositoryResult<Unit>

    /**
     * 改写待办的提醒时间（提醒页「取消提醒」传 null、「延后」传新时间）。
     * 必须回写源头，否则 `todo_items.remindAt` 与实际提醒实例会分叉。
     */
    suspend fun updateRemindAt(id: Long, remindAt: Long?, updatedAt: Long): RepositoryResult<Unit>

    /**
     * 删除待办本体。**只删 `todo_items` 一行** ——
     * TODO 提醒实例的清理由调用方经 `TodoReminderSync.cancel` 一并完成（单一事实来源）。
     */
    suspend fun deleteTodo(id: Long): RepositoryResult<Unit>
}
