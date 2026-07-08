package com.example.zhilu.domain.reminder

import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderStatus
import com.example.zhilu.domain.model.ReminderType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderClassifierTest {
    private val startOfToday = 1_000_000L
    private val day = 86_400_000L
    private val classifier = ReminderClassifier()

    @Test
    fun groupsScheduledRemindersByDueTimeAndCompletedStatus() {
        val overdue = reminder(id = 1, dueAt = startOfToday - 1)
        val today = reminder(id = 2, dueAt = startOfToday + 3_000)
        val future = reminder(id = 3, dueAt = startOfToday + day + 1)
        val done = reminder(id = 4, dueAt = startOfToday, status = ReminderStatus.DONE)

        val bucket = classifier.classify(
            reminders = listOf(future, done, overdue, today),
            now = startOfToday + 2_000,
            startOfToday = startOfToday
        )

        assertEquals(listOf(today), bucket.today)
        assertEquals(listOf(overdue), bucket.overdue)
        assertEquals(listOf(future), bucket.future)
        assertEquals(listOf(done), bucket.completed)
    }

    @Test
    fun classifiesExactNowAndEndOfTodayBoundaries() {
        val now = startOfToday + 2_000
        val dueBeforeNowToday = reminder(id = 1, dueAt = now - 1)
        val dueAtNow = reminder(id = 2, dueAt = now)
        val dueAtEndOfTodayMinusOne = reminder(id = 3, dueAt = startOfToday + day - 1)
        val dueAtEndOfToday = reminder(id = 4, dueAt = startOfToday + day)

        val bucket = classifier.classify(
            reminders = listOf(dueAtEndOfToday, dueAtEndOfTodayMinusOne, dueAtNow, dueBeforeNowToday),
            now = now,
            startOfToday = startOfToday
        )

        assertEquals(listOf(dueBeforeNowToday), bucket.overdue)
        assertEquals(listOf(dueAtNow, dueAtEndOfTodayMinusOne), bucket.today)
        assertEquals(listOf(dueAtEndOfToday), bucket.future)
        assertEquals(emptyList<ReminderInstance>(), bucket.completed)
    }

    @Test
    fun treatsFiredAsActiveAndExcludesCanceled() {
        val now = startOfToday + 2_000
        val firedOverdue = reminder(id = 1, dueAt = now - 1, status = ReminderStatus.FIRED)
        val firedToday = reminder(id = 2, dueAt = now, status = ReminderStatus.FIRED)
        val canceled = reminder(id = 3, dueAt = now, status = ReminderStatus.CANCELED)

        val bucket = classifier.classify(
            reminders = listOf(canceled, firedToday, firedOverdue),
            now = now,
            startOfToday = startOfToday
        )

        assertEquals(listOf(firedOverdue), bucket.overdue)
        assertEquals(listOf(firedToday), bucket.today)
        assertEquals(emptyList<ReminderInstance>(), bucket.future)
        assertEquals(emptyList<ReminderInstance>(), bucket.completed)
    }

    private fun reminder(
        id: Long,
        dueAt: Long,
        status: ReminderStatus = ReminderStatus.SCHEDULED
    ) = ReminderInstance(
        id = id,
        type = ReminderType.REVIEW,
        sourceId = id,
        noteId = id,
        dueAt = dueAt,
        status = status,
        notificationId = id.toInt()
    )
}
