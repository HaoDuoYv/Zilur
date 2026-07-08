package com.example.zhilu.data.local.mapper

import com.example.zhilu.data.local.entity.MediaEntity
import com.example.zhilu.domain.model.Media

object MediaMapper {
    fun toDomain(entity: MediaEntity): Media = Media(
        id = entity.id,
        uri = entity.uri,
        width = entity.width,
        height = entity.height,
        size = entity.size,
        createdAt = entity.createdAt
    )

    fun toEntity(domain: Media): MediaEntity = MediaEntity(
        id = domain.id,
        uri = domain.uri,
        width = domain.width,
        height = domain.height,
        size = domain.size,
        createdAt = domain.createdAt
    )
}
