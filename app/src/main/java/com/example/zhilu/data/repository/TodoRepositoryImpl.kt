package com.example.zhilu.data.repository

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.local.dao.TodoDao
import com.example.zhilu.data.local.mapper.TodoMapper
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.domain.repository.TodoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class TodoRepositoryImpl(
    private val todoDao: TodoDao
) : TodoRepository {
    override fun observeByNoteId(noteId: Long): Flow<RepositoryResult<List<TodoItem>>> =
        todoDao.observeByNoteId(noteId)
            .map { entities -> RepositoryResult.Success(entities.map(TodoMapper::toDomain)) as RepositoryResult<List<TodoItem>> }
            .catch { e -> emit(RepositoryResult.Error("Failed to load todos", e)) }

    override suspend fun addTodo(todo: TodoItem): RepositoryResult<Long> = runCatching {
        todoDao.insert(TodoMapper.toEntity(todo))
    }.toRepositoryResult("Failed to save todo")

    override suspend fun updateTodo(todo: TodoItem): RepositoryResult<Unit> = runCatching {
        todoDao.update(TodoMapper.toEntity(todo.copy(updatedAt = System.currentTimeMillis())))
    }.toRepositoryResult("Failed to update todo")

    override suspend fun completeTodo(id: Long, completedAt: Long): RepositoryResult<Unit> = runCatching {
        val todo = todoDao.getById(id)?.let(TodoMapper::toDomain) ?: return@runCatching
        todoDao.update(
            TodoMapper.toEntity(
                todo.copy(
                    completedAt = completedAt,
                    updatedAt = completedAt
                )
            )
        )
    }.toRepositoryResult("Failed to complete todo")
}
