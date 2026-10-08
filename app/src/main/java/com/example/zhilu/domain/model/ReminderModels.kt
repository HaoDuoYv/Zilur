package com.example.zhilu.domain.model

data class ReminderInstance(
    val id: Long = 0,
    val type: ReminderType,
    val sourceId: Long,
    val noteId: Long? = null,
    val dueAt: Long,
    val status: ReminderStatus = ReminderStatus.SCHEDULED,
    val notificationId: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val firedAt: Long? = null
)

enum class ReminderType(val value: Int) {
    REVIEW(1),
    TODO(2);

    companion object {
        fun fromValue(value: Int): ReminderType =
            entries.firstOrNull { it.value == value } ?: REVIEW
    }
}

enum class ReminderStatus(val value: Int) {
    SCHEDULED(1),
    FIRED(2),
    DONE(3),
    CANCELED(4);

    companion object {
        fun fromValue(value: Int): ReminderStatus =
            entries.firstOrNull { it.value == value } ?: SCHEDULED
    }
}

data class ReminderBucket(
    val today: List<ReminderInstance> = emptyList(),
    val overdue: List<ReminderInstance> = emptyList(),
    val future: List<ReminderInstance> = emptyList(),
    val completed: List<ReminderInstance> = emptyList()
)

/** 提醒 + 展示上下文：笔记标题、待办文本（TODO）、当前档位（REVIEW）。 */
data class ReminderWithContext(
    val reminder: ReminderInstance,
    val noteTitle: String? = null,
    val todoContent: String? = null,
    val reviewStep: Int? = null
)
