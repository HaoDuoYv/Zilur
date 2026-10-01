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
    private val tagDao: TagDao
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

    override suspend fun updateTag(tag: Tag): RepositoryResult<Unit> = runCatching {
        tagDao.update(TagMapper.toEntity(tag))
    }.toRepositoryResult("Failed to update tag")

    override suspend fun deleteTag(tag: Tag): RepositoryResult<Unit> = runCatching {
        tagDao.delete(TagMapper.toEntity(tag))
    }.toRepositoryResult("Failed to delete tag")

    override fun getTagCount(): Flow<RepositoryResult<Int>> = tagDao.countFlow()
        .map<Int, RepositoryResult<Int>> { RepositoryResult.Success(it) }
        .catch { e -> emit(RepositoryResult.Error("Failed to count tags", e)) }

    override fun getNoteCountsByTag(): Flow<RepositoryResult<Map<Long, Int>>> = tagDao.countNotesPerTag()
        .map<List<TagNoteCount>, RepositoryResult<Map<Long, Int>>> { rows ->
            RepositoryResult.Success(rows.associate { it.tagId to it.noteCount })
        }
        .catch { e -> emit(RepositoryResult.Error("Failed to count notes per tag", e)) }
}
