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

    /**
     * 每个标签下的**未删除**笔记数，键为标签 id。没有笔记的标签不会出现在结果里，取用方按 0 兜底。
     *
     * 计数在数据层聚合（单条 `GROUP BY`），取用方不必自行拉取全量笔记做统计。
     */
    fun getNoteCountsByTag(): Flow<RepositoryResult<Map<Long, Int>>>
}
