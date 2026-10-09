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

/**
 * 待办 + 展示上下文（**查询投影**，不是实体）。
 *
 * 全局「待办」档的每一行都要回答"来自哪篇笔记、能不能点进去"：
 * - [noteTitle]：来源笔记标题，笔记被删除 / 不存在时为 null；
 * - [noteAlive]：笔记存在且不在回收站 —— 为 false 时行内不给"打开笔记"入口。
 */
data class TodoWithContext(
    val todo: TodoItem,
    val noteTitle: String? = null,
    val noteAlive: Boolean = false
)
