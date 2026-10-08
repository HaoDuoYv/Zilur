package com.example.zhilu.data.local.entity

import androidx.room.Embedded

/**
 * 提醒 + 展示上下文的查询投影（**不是表实体**，仅供 Room 查询映射）。
 *
 * 提醒行要回答"这是哪篇笔记的什么事"：
 * - [noteTitle]：LEFT JOIN `notes`（笔记被硬删除时为 null）；
 * - [todoContent]：LEFT JOIN `todo_items`，仅 `type = TODO` 时有值；
 * - [reviewStep]：LEFT JOIN `review_plans`，仅 `type = REVIEW` 时有值，用于「第 N/5 次」。
 */
data class ReminderWithContextRow(
    @Embedded val reminder: ReminderInstanceEntity,
    val noteTitle: String?,
    val todoContent: String?,
    val reviewStep: Int?
)
