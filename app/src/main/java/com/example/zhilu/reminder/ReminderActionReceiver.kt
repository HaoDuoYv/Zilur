package com.example.zhilu.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.ReminderType
import com.example.zhilu.domain.repository.ReminderRepository
import com.example.zhilu.domain.usecase.ResolveReminderUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 通知上的动作按钮：**待办提醒**的「完成」与「延后 1 小时」。
 *
 * 动作语义与提醒列表里的同名动作走**同一个用例**（[ResolveReminderUseCase]）——
 * 通知栏只是第三个入口，不该有第二套"完成"的定义（不然笔记里的待办打不上勾）。
 * 不直接信任 intent 里带的字段：先按 `(type, sourceId)` 去库里取**当前活跃的提醒实例**，
 * 拿真实数据调用用例（通知发出后提醒可能已被应用内处理掉，取不到就只清通知）。
 *
 * REVIEW 类提醒没有动作按钮（计划自己管提醒的生灭，UI 各入口都只读），
 * 但 receiver 仍按 type 分派，未来加动作不用改结构。
 */
@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var resolveReminder: ResolveReminderUseCase

    // 接收器实例随广播创建、返回即弃；作用域只为把 goAsync 的窗口撑到写库完成。
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_COMPLETE && action != ACTION_SNOOZE_HOUR) return

        val sourceId = intent.getLongExtra(EXTRA_SOURCE_ID, -1L)
        val type = ReminderType.fromValue(intent.getIntExtra(EXTRA_REMINDER_TYPE, ReminderType.TODO.value))
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, Int.MIN_VALUE)
        if (sourceId <= 0L) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                val active = reminderRepository.getActiveReminder(type, sourceId)
                val reminder = (active as? RepositoryResult.Success)?.data
                if (reminder == null) {
                    // 提醒已被处理（比如刚在应用里完成）—— 这条通知已经是过期的，顺手清掉
                    cancelNotification(context, notificationId)
                    return@launch
                }

                val now = System.currentTimeMillis()
                val result = when (action) {
                    ACTION_COMPLETE -> resolveReminder.complete(reminder, now)
                    else -> resolveReminder.snooze(reminder, now + SNOOZE_HOUR_MILLIS, now)
                }

                when (result) {
                    is RepositoryResult.Success -> cancelNotification(context, notificationId)
                    is RepositoryResult.Error -> Timber.w(result.throwable, result.message)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun cancelNotification(context: Context, notificationId: Int) {
        if (notificationId == Int.MIN_VALUE) return
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    companion object {
        /** 「完成」：提醒置 DONE + 回写 `todo_items.completedAt`（经 [ResolveReminderUseCase]）。 */
        const val ACTION_COMPLETE = "com.example.zhilu.action.REMINDER_COMPLETE"

        /** 「延后 1 小时」：提醒重建到一小时后 + 回写 `todo_items.remindAt`。 */
        const val ACTION_SNOOZE_HOUR = "com.example.zhilu.action.REMINDER_SNOOZE_HOUR"

        const val EXTRA_REMINDER_TYPE = "com.example.zhilu.extra.REMINDER_TYPE"
        const val EXTRA_SOURCE_ID = "com.example.zhilu.extra.SOURCE_ID"
        const val EXTRA_NOTIFICATION_ID = "com.example.zhilu.extra.NOTIFICATION_ID"

        private const val SNOOZE_HOUR_MILLIS = 3_600_000L
    }
}
