package com.example.zhilu.domain.reminder

import com.example.zhilu.domain.model.ReminderBucket
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderStatus

class ReminderClassifier {
    fun classify(
        reminders: List<ReminderInstance>,
        now: Long,
        startOfToday: Long
    ): ReminderBucket {
        val endOfToday = startOfToday + DAY
        val active = reminders.filter {
            it.status == ReminderStatus.SCHEDULED || it.status == ReminderStatus.FIRED
        }
        return ReminderBucket(
            today = active.filter { it.dueAt in now until endOfToday }.sortedBy { it.dueAt },
            overdue = active.filter { it.dueAt < now }.sortedBy { it.dueAt },
            future = active.filter { it.dueAt >= endOfToday }.sortedBy { it.dueAt },
            completed = reminders.filter { it.status == ReminderStatus.DONE }.sortedByDescending { it.updatedAt }
        )
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
