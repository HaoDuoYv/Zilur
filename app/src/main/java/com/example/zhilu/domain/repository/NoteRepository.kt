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
     *
     * [limit] 必须显式给出调用场景的量级：[DEFAULT_SEARCH_LIMIT] 给 UI，
     * [AI_SEARCH_LIMIT] 给 AI 工具 —— 两者语义不同，**不要共用一个常量**。
     */
    suspend fun searchNotes(
        keyword: String,
        tagIds: List<Long> = emptyList(),
        limit: Int = DEFAULT_SEARCH_LIMIT
    ): RepositoryResult<List<Note>>
    suspend fun getNotesByTagId(tagId: Long): RepositoryResult<List<Note>>
    suspend fun insertNote(note: Note): RepositoryResult<Note>
    suspend fun updateNote(note: Note): RepositoryResult<Note>
    suspend fun deleteNote(note: Note): RepositoryResult<Unit>
    suspend fun softDeleteNote(id: Long): RepositoryResult<Unit>
    suspend fun restoreNote(id: Long): RepositoryResult<Unit>
    suspend fun clearDeletedNotes(): RepositoryResult<Unit>
    suspend fun getNoteCount(): RepositoryResult<Int>

    companion object {
        /** UI 搜索：够看全，又防「高频字命中全库」把结果集撑爆（无分页，全量进列表）。 */
        const val DEFAULT_SEARCH_LIMIT = 200

        /** AI 工具搜索：结果按条进上下文预算，必须小而准（配合命中摘要）。 */
        const val AI_SEARCH_LIMIT = 10
    }
}
