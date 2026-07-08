package com.example.zhilu.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.zhilu.MainActivity
import com.example.zhilu.domain.model.ReminderInstance
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun showReminder(reminder: ReminderInstance): Boolean {
        ensureNotificationChannel()

        if (!canPostNotifications()) {
            return false
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Reminder due")
            .setContentText("Open ZhiLu to review this reminder.")
            .setContentIntent(contentIntent(reminder))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(reminder.notificationId, notification)
        return true
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Due reminders"
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun contentIntent(reminder: ReminderInstance): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_REMINDER
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            reminder.noteId?.let { putExtra(EXTRA_NOTE_ID, it) }
                ?: putExtra(EXTRA_REMINDER_CENTER, true)
        }

        return PendingIntent.getActivity(
            context,
            reminder.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "zhilu_reminders"
        const val EXTRA_NOTE_ID = "com.example.zhilu.extra.NOTE_ID"
        const val EXTRA_REMINDER_CENTER = "com.example.zhilu.extra.REMINDER_CENTER"

        private const val CHANNEL_NAME = "Reminders"
        private const val ACTION_OPEN_REMINDER = "com.example.zhilu.action.OPEN_REMINDER"
    }
}
