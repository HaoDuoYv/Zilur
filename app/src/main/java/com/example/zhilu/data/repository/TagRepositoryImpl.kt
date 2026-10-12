package com.example.zhilu.data.repository

import com.example.zhilu.data.local.dao.TagDao
import com.example.zhilu.data.local.dao.TagNoteCount
import com.example.zhilu.data.local.mapper.TagMapper
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class TagRepositoryImpl(
    private val tagDao: TagDao,
    private val searchIndexer: NoteSearchIndexer,
    private val transactionRunner: RepositoryTransactionRunner = NoOpRepositoryTransactionRunner
) : TagRepository {
    override fun getAllTags(): Flow<RepositoryResult<List<Tag>>> = tagDao.getAll()
        .map { entities -> RepositoryResult.Success(entities.map(TagMapper::toDomain)) as RepositoryResult<List<Tag>> }
        .catch { e -> emit(RepositoryResult.Error("Failed to load tags", e)) }

    override suspend fun getTagById(id: Long): RepositoryResult<Tag?> = runCatching {
        tagDao.getById(id)?.let(TagMapper::toDomain)
    }.toRepositoryResult("Failed to load tag")

    override suspend fun getTagByName(name: String): RepositoryResult<Tag?> = runCatching {
        tagDao.getByName(name)?.let(TagMapper::toDomain)
    }.toRepositoryResult("Failed to load tag")

    override suspend fun getTagsByNoteId(noteId: Long): RepositoryResult<List<Tag>> = runCatching {
        tagDao.getByNoteId(noteId).map(TagMapper::toDomain)
    }.toRepositoryResult("Failed to load tags")

    override suspend fun insertTag(tag: Tag): RepositoryResult<Long> = runCatching {
        tagDao.insert(TagMapper.toEntity(tag))
    }.toRepositoryResult("Failed to save tag")

    /**
     * 改名要连带重建**关联笔记**的检索索引：标签名是索引正文的一部分，
     * 不重建就会"搜旧名还能命中、搜新名搜不到"。
     */
    override suspend fun updateTag(tag: Tag): RepositoryResult<Unit> = runCatching {
        transactionRunner.runInTransaction {
            tagDao.update(TagMapper.toEntity(tag))
            searchIndexer.reindex(tagDao.noteIdsForTags(listOf(tag.id)))
        }
    }.toRepositoryResult("Failed to update tag")

    /**
     * 删除同理：先取关联笔记（删完就查不到了），删标签后重建 ——
     * 重建读到的是删除后的状态，该标签名自然从索引里消失。
     */
    override suspend fun deleteTag(tag: Tag): RepositoryResult<Unit> = runCatching {
        transactionRunner.runInTransaction {
            val affected = tagDao.noteIdsForTags(listOf(tag.id))
            tagDao.delete(TagMapper.toEntity(tag))
            searchIndexer.reindex(affected)
        }
    }.toRepositoryResult("Failed to delete tag")

    override suspend fun mergeTags(sourceIds: List<Long>, targetId: Long): RepositoryResult<Unit> =
        runCatching {
            val sources = sourceIds.filter { it != targetId }
            if (sources.isEmpty()) return@runCatching
            transactionRunner.runInTransaction {
                // 受影响的是**源标签**关联的笔记：合并后它们的标签集合里
                // 源名换成目标名。目标标签关联的笔记集合不变，无需重建。
                val affected = tagDao.noteIdsForTags(sources)
                // 顺序固定：先改挂关联、再清源关联、最后删源标签。
                // 任何一步失败都会整体回滚，不会留下"关联丢了、标签还在"的半截状态。
                tagDao.repointNoteTags(sourceTagIds = sources, targetId = targetId)
                tagDao.deleteNoteTagsByTagIds(sources)
                tagDao.deleteTagsByIds(sources)
                searchIndexer.reindex(affected)
            }
        }.toRepositoryResult("Failed to merge tags")

    override fun getTagCount(): Flow<RepositoryResult<Int>> = tagDao.countFlow()
        .map<Int, RepositoryResult<Int>> { RepositoryResult.Success(it) }
        .catch { e -> emit(RepositoryResult.Error("Failed to count tags", e)) }

    override fun getNoteCountsByTag(): Flow<RepositoryResult<Map<Long, Int>>> = tagDao.countNotesPerTag()
        .map<List<TagNoteCount>, RepositoryResult<Map<Long, Int>>> { rows ->
            RepositoryResult.Success(rows.associate { it.tagId to it.noteCount })
        }
        .catch { e -> emit(RepositoryResult.Error("Failed to count notes per tag", e)) }
}
