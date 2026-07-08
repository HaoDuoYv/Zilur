package com.example.zhilu.domain.model

data class Note(
    val id: Long = 0,
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val deletedAt: Long? = null,
    val blocks: List<Block> = emptyList(),
    val tags: List<Tag> = emptyList()
)
