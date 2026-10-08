package com.example.zhilu.domain.repository

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.TodoItem
import kotlinx.coroutines.flow.Flow

interface TodoRepository {
    fun observeByNoteId(noteId: Long): Flow<RepositoryResult<List<TodoItem>>>
    suspend fun addTodo(todo: TodoItem): RepositoryResult<Long>
    suspend fun updateTodo(todo: TodoItem): RepositoryResult<Unit>
    suspend fun completeTodo(id: Long, completedAt: Long): RepositoryResult<Unit>

    /**
     * 改写待办的提醒时间（提醒页「取消提醒」传 null、「延后」传新时间）。
     * 必须回写源头，否则 `todo_items.remindAt` 与实际提醒实例会分叉。
     */
    suspend fun updateRemindAt(id: Long, remindAt: Long?, updatedAt: Long): RepositoryResult<Unit>
}
