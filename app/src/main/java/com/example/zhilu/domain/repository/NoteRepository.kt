package com.example.zhilu.domain.repository

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun getAllNotes(): Flow<RepositoryResult<List<Note>>>
    fun getFavoriteNotes(): Flow<RepositoryResult<List<Note>>>
    fun getDeletedNotes(): Flow<RepositoryResult<List<Note>>>
    suspend fun getNoteById(id: Long): RepositoryResult<Note?>

    /**
     * 搜索笔记：关键词 × 标签集合（AND）组合查询。
     *
     * - [keyword] 为空且 [tagIds] 为空 → 空列表（保持旧行为，不意外全量返回）；
     * - [keyword] 为空但 [tagIds] 非空 → 纯标签浏览；
     * - [tagIds] 为空 → 纯关键词搜索。
     */
    suspend fun searchNotes(keyword: String, tagIds: List<Long> = emptyList()): RepositoryResult<List<Note>>
    suspend fun getNotesByTagId(tagId: Long): RepositoryResult<List<Note>>
    suspend fun insertNote(note: Note): RepositoryResult<Note>
    suspend fun updateNote(note: Note): RepositoryResult<Note>
    suspend fun deleteNote(note: Note): RepositoryResult<Unit>
    suspend fun softDeleteNote(id: Long): RepositoryResult<Unit>
    suspend fun restoreNote(id: Long): RepositoryResult<Unit>
    suspend fun clearDeletedNotes(): RepositoryResult<Unit>
    suspend fun getNoteCount(): RepositoryResult<Int>
}
