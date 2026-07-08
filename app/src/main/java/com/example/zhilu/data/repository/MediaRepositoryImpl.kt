package com.example.zhilu.data.repository

import com.example.zhilu.data.local.dao.MediaDao
import com.example.zhilu.data.local.mapper.MediaMapper
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class MediaRepositoryImpl(
    private val mediaDao: MediaDao
) : MediaRepository {
    override suspend fun getAllMedia(): RepositoryResult<List<Media>> = runCatching {
        mediaDao.getAll().map(MediaMapper::toDomain)
    }.toRepositoryResult("Failed to load media")

    override suspend fun getMediaById(id: Long): RepositoryResult<Media?> = runCatching {
        mediaDao.getById(id)?.let(MediaMapper::toDomain)
    }.toRepositoryResult("Failed to load media")

    override suspend fun insertMedia(media: Media): RepositoryResult<Long> = runCatching {
        mediaDao.insert(MediaMapper.toEntity(media))
    }.toRepositoryResult("Failed to save media")

    override suspend fun updateMedia(media: Media): RepositoryResult<Unit> = runCatching {
        mediaDao.update(MediaMapper.toEntity(media))
    }.toRepositoryResult("Failed to update media")

    override suspend fun deleteMedia(media: Media): RepositoryResult<Unit> = runCatching {
        mediaDao.delete(MediaMapper.toEntity(media))
    }.toRepositoryResult("Failed to delete media")

    override fun getMediaCount(): Flow<RepositoryResult<Int>> = mediaDao.countFlow()
        .map<Int, RepositoryResult<Int>> { RepositoryResult.Success(it) }
        .catch { e -> emit(RepositoryResult.Error("Failed to count media", e)) }

    override suspend fun getTotalSize(): RepositoryResult<Long> = runCatching {
        mediaDao.totalSize()
    }.toRepositoryResult("Failed to sum media size")
}
