package com.example.zhilu.data.local.entity

import androidx.room.Embedded

/**
 * 待办 + 展示上下文的查询投影（**不是表实体**，仅供 Room 查询映射）。
 *
 * 全局「待办」档要回答"这条待办来自哪篇笔记"：
 * - [noteTitle]：LEFT JOIN `notes`，仅**未删除**的笔记有值；
 * - [noteAlive]：笔记存在且不在回收站 —— 决定行内是否显示标题、
 *   以及「点进笔记」是否可用（回收站里的笔记不该把用户带进一个打不开的编辑页）。
 */
data class TodoWithContextRow(
    @Embedded val todo: TodoItemEntity,
    val noteTitle: String?,
    val noteAlive: Boolean
)
