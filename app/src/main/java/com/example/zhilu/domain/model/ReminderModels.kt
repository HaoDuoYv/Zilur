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
) {
    /**
     * 「待处理」子标签的口径：今天 + 未来。
     *
     * **不含 [overdue]** —— 它与「已逾期」子标签并列展示，重复计入会让三档数字
     * 相加超过总数（踩过：待处理 3 + 已逾期 1 + 已完成 2 = 6，而总数只有 5）。
     * 顶部大档位的「提醒 N」另用「未完成总数（含逾期）」口径，不共用此值。
     */
    val pendingCount: Int get() = today.size + future.size
}

/** 提醒 + 展示上下文：笔记标题、待办文本（TODO）、当前档位（REVIEW）。 */
data class ReminderWithContext(
    val reminder: ReminderInstance,
    val noteTitle: String? = null,
    val todoContent: String? = null,
    val reviewStep: Int? = null
)
