package com.example.zhilu.domain.model

data class TodoItem(
    val id: Long = 0,
    val noteId: Long? = null,
    val content: String = "",
    val remindAt: Long? = null,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
) {
    val isCompleted: Boolean
        get() = completedAt != null
}
