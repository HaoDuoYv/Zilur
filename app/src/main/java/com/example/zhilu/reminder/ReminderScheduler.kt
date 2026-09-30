package com.example.zhilu.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ReminderScheduler @Inject constructor(
    private val workEnqueuer: WorkEnqueuer
) {
    fun schedulePeriodicChecks() {
        workEnqueuer.enqueueUniquePeriodicWork(
            name = PERIODIC_WORK,
            repeatIntervalMillis = MIN_PERIODIC_INTERVAL_MILLIS
        )
    }

    fun scheduleOneTimeCheck() {
        workEnqueuer.enqueueUniqueOneTimeWork(ONE_TIME_WORK)
    }

    fun cancelAll() {
        workEnqueuer.cancelAll()
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            schedulePeriodicChecks()
        } else {
            cancelAll()
        }
    }

    interface WorkEnqueuer {
        fun enqueueUniquePeriodicWork(name: String, repeatIntervalMillis: Long)
        fun enqueueUniqueOneTimeWork(name: String)
        fun cancelAll()
    }

    class WorkManagerWorkEnqueuer @Inject constructor(
        @ApplicationContext private val context: Context
    ) : WorkEnqueuer {
        override fun enqueueUniquePeriodicWork(
            name: String,
            repeatIntervalMillis: Long
        ) {
            val request = PeriodicWorkRequestBuilder<ReminderCheckWorker>(
                repeatIntervalMillis,
                TimeUnit.MILLISECONDS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                name,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        override fun enqueueUniqueOneTimeWork(name: String) {
            val request = OneTimeWorkRequestBuilder<ReminderCheckWorker>().build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                name,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        override fun cancelAll() {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
            WorkManager.getInstance(context).cancelUniqueWork(ONE_TIME_WORK)
        }
    }

    companion object {
        const val PERIODIC_WORK = "periodic-reminder-check"
        const val ONE_TIME_WORK = "one-time-reminder-check"
        const val MIN_PERIODIC_INTERVAL_MILLIS = 15 * 60 * 1000L
    }
}
