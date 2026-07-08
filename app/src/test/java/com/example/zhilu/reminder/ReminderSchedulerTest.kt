package com.example.zhilu.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderSchedulerTest {
    private val enqueuer = RecordingWorkEnqueuer()
    private val scheduler = ReminderScheduler(enqueuer)

    @Test
    fun schedulePeriodicChecksEnqueuesUniquePeriodicReminderWork() {
        scheduler.schedulePeriodicChecks()

        assertEquals(
            listOf(
                RecordedWork.Periodic(
                    name = ReminderScheduler.PERIODIC_WORK,
                    repeatIntervalMillis = 15 * 60 * 1000L
                )
            ),
            enqueuer.recorded
        )
    }

    @Test
    fun scheduleOneTimeCheckEnqueuesUniqueOneTimeReminderWork() {
        scheduler.scheduleOneTimeCheck()

        assertEquals(
            listOf(RecordedWork.OneTime(ReminderScheduler.ONE_TIME_WORK)),
            enqueuer.recorded
        )
    }

    private class RecordingWorkEnqueuer : ReminderScheduler.WorkEnqueuer {
        val recorded = mutableListOf<RecordedWork>()

        override fun enqueueUniquePeriodicWork(
            name: String,
            repeatIntervalMillis: Long
        ) {
            recorded += RecordedWork.Periodic(name, repeatIntervalMillis)
        }

        override fun enqueueUniqueOneTimeWork(name: String) {
            recorded += RecordedWork.OneTime(name)
        }
    }

    private sealed class RecordedWork {
        data class Periodic(
            val name: String,
            val repeatIntervalMillis: Long
        ) : RecordedWork()

        data class OneTime(val name: String) : RecordedWork()
    }
}
