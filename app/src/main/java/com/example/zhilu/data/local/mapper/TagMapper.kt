package com.example.zhilu.data.local.mapper

import com.example.zhilu.data.local.entity.TagEntity
import com.example.zhilu.domain.model.Tag

object TagMapper {
    fun toDomain(entity: TagEntity): Tag = Tag(
        id = entity.id,
        name = entity.name,
        color = entity.color
    )

    fun toEntity(domain: Tag): TagEntity = TagEntity(
        id = domain.id,
        name = domain.name,
        color = domain.color
    )
}
