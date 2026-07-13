package com.example.zhilu.domain.repository

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun getAllNotes(): Flow<RepositoryResult<List<Note>>>
    fun getFavoriteNotes(): Flow<RepositoryResult<List<Note>>>
    fun getDeletedNotes(): Flow<RepositoryResult<List<Note>>>
    suspend fun getNoteById(id: Long): RepositoryResult<Note?>
    suspend fun searchNotes(keyword: String): RepositoryResult<List<Note>>
    suspend fun getNotesByTagId(tagId: Long): RepositoryResult<List<Note>>
    suspend fun insertNote(note: Note): RepositoryResult<Note>
    suspend fun updateNote(note: Note): RepositoryResult<Note>
    suspend fun deleteNote(note: Note): RepositoryResult<Unit>
    suspend fun softDeleteNote(id: Long): RepositoryResult<Unit>
    suspend fun restoreNote(id: Long): RepositoryResult<Unit>
    suspend fun clearDeletedNotes(): RepositoryResult<Unit>
    suspend fun getNoteCount(): RepositoryResult<Int>
}
