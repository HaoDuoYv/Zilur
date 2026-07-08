package com.example.zhilu.domain.repository

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.Tag
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    fun getAllTags(): Flow<RepositoryResult<List<Tag>>>
    suspend fun getTagById(id: Long): RepositoryResult<Tag?>
    suspend fun getTagByName(name: String): RepositoryResult<Tag?>
    suspend fun getTagsByNoteId(noteId: Long): RepositoryResult<List<Tag>>
    suspend fun insertTag(tag: Tag): RepositoryResult<Long>
    suspend fun updateTag(tag: Tag): RepositoryResult<Unit>
    suspend fun deleteTag(tag: Tag): RepositoryResult<Unit>
    fun getTagCount(): Flow<RepositoryResult<Int>>
}
