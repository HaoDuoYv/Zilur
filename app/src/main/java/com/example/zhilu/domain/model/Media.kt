package com.example.zhilu.domain.model

data class Media(
    val id: Long = 0,
    val uri: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val size: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
