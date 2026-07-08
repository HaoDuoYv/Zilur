package com.example.zhilu.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.repository.ReminderRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class ReminderCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val reminderRepository: ReminderRepository,
    private val reminderNotifier: ReminderNotifier
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()

        return when (val dueReminders = reminderRepository.getDueReminders(now)) {
            is RepositoryResult.Error -> {
                Timber.w(dueReminders.throwable, dueReminders.message)
                Result.retry()
            }

            is RepositoryResult.Success -> {
                for (reminder in dueReminders.data) {
                    val notificationShown = runCatching { reminderNotifier.showReminder(reminder) }
                        .onFailure { Timber.w(it, "Failed to show reminder notification") }
                        .getOrDefault(false)

                    // Missing permission or notification display failures leave the reminder scheduled.
                    // WorkManager retry is reserved for repository failures so permission denial does not loop.
                    if (!notificationShown) {
                        continue
                    }

                    when (val fired = reminderRepository.markFired(reminder, firedAt = now)) {
                        is RepositoryResult.Error -> {
                            Timber.w(fired.throwable, fired.message)
                            return Result.retry()
                        }

                        is RepositoryResult.Success -> Unit
                    }
                }

                Result.success()
            }
        }
    }
}
