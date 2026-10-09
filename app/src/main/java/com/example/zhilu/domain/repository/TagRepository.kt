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

    /**
     * 合并标签：把 [sourceIds] 的笔记关联全部改挂到 [targetId]，然后删除源标签。
     *
     * 合并是"改名"之外的另一种收束方式：目标标签**保留自己的名字与颜色**，
     * 源标签消失、关联不断、正文文本不动（标签不是从正文解析出来的）。
     * 在单个事务里先改关联（`INSERT OR IGNORE` 避开唯一约束）再删源标签 ——
     * 中途失败不会留下"关联丢了、标签还在"的半截状态。
     */
    suspend fun mergeTags(sourceIds: List<Long>, targetId: Long): RepositoryResult<Unit>

    fun getTagCount(): Flow<RepositoryResult<Int>>

    /**
     * 每个标签下的**未删除**笔记数，键为标签 id。没有笔记的标签不会出现在结果里，取用方按 0 兜底。
     *
     * 计数在数据层聚合（单条 `GROUP BY`），取用方不必自行拉取全量笔记做统计。
     */
    fun getNoteCountsByTag(): Flow<RepositoryResult<Map<Long, Int>>>
}
