package com.example.zhilu.domain.repository

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.Media
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    suspend fun getAllMedia(): RepositoryResult<List<Media>>
    suspend fun getMediaById(id: Long): RepositoryResult<Media?>
    suspend fun insertMedia(media: Media): RepositoryResult<Long>
    suspend fun updateMedia(media: Media): RepositoryResult<Unit>
    suspend fun deleteMedia(media: Media): RepositoryResult<Unit>
    fun getMediaCount(): Flow<RepositoryResult<Int>>
    suspend fun getTotalSize(): RepositoryResult<Long>
}
