package com.example.zhilu.domain.repository

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.TodoItem
import kotlinx.coroutines.flow.Flow

interface TodoRepository {
    fun observeByNoteId(noteId: Long): Flow<RepositoryResult<List<TodoItem>>>
    suspend fun addTodo(todo: TodoItem): RepositoryResult<Long>
    suspend fun updateTodo(todo: TodoItem): RepositoryResult<Unit>
    suspend fun completeTodo(id: Long, completedAt: Long): RepositoryResult<Unit>
}
