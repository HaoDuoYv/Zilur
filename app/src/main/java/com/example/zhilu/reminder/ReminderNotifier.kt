package com.example.zhilu.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.annotation.SuppressLint
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.zhilu.MainActivity
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * 提醒通知（复习 + 待办共用一个渠道）。
 *
 * 文案按类型分叉；**待办提醒**额外挂两个动作按钮（完成 / 延后 1 小时），
 * 由 [ReminderActionReceiver] 处理（与列表同名动作共用 `ResolveReminderUseCase`）。
 * 复习提醒不加按钮：计划自己管提醒的生灭，各入口都只读。
 */
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @SuppressLint("MissingPermission")
    fun showReminder(reminder: ReminderInstance): Boolean {
        ensureNotificationChannel()

        if (!canPostNotifications()) {
            return false
        }

        val isReview = reminder.type == ReminderType.REVIEW
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (isReview) "复习提醒" else "待办提醒")
            .setContentText(
                if (isReview) "有笔记到期，点开开始复习。" else "有待办到期，点开查看。"
            )
            .setContentIntent(contentIntent(reminder))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (!isReview) {
            builder.addAction(0, "完成", actionIntent(reminder, ReminderActionReceiver.ACTION_COMPLETE))
            builder.addAction(
                0,
                "延后 1 小时",
                actionIntent(reminder, ReminderActionReceiver.ACTION_SNOOZE_HOUR)
            )
        }

        NotificationManagerCompat.from(context).notify(reminder.notificationId, builder.build())
        return true
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESCRIPTION
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

    /** 动作按钮的广播意图；requestCode 由「通知 id + 动作」混出，按钮之间互不覆盖。 */
    private fun actionIntent(reminder: ReminderInstance, action: String): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderActionReceiver.EXTRA_REMINDER_TYPE, reminder.type.value)
            putExtra(ReminderActionReceiver.EXTRA_SOURCE_ID, reminder.sourceId)
            putExtra(ReminderActionReceiver.EXTRA_NOTIFICATION_ID, reminder.notificationId)
        }

        return PendingIntent.getBroadcast(
            context,
            reminder.notificationId * 31 + action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "zhilu_reminders"
        const val EXTRA_NOTE_ID = "com.example.zhilu.extra.NOTE_ID"
        const val EXTRA_REMINDER_CENTER = "com.example.zhilu.extra.REMINDER_CENTER"

        private const val CHANNEL_NAME = "提醒"
        private const val CHANNEL_DESCRIPTION = "到期的复习与待办提醒"
        private const val ACTION_OPEN_REMINDER = "com.example.zhilu.action.OPEN_REMINDER"
    }
}
