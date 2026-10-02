package com.example.zhilu.ai

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber

/**
 * AI 生成的前台服务。
 *
 * 生成协程本身跑在 [AiTaskManager] 的进程级 scope 上，**这里不搬任何业务逻辑**；
 * 它存在的唯一意义是把进程从「切到后台随时可能被回收」提升为前台进程，
 * 并在通知栏持续暴露进度与一个「停止」按钮。
 *
 * 生命周期由 [AndroidAiTaskHost] 依据任务快照驱动：有活跃任务即启动，全部结束即停止——
 * 所以本服务不需要自己判断什么时候该退出。
 */
@AndroidEntryPoint
class AiTaskService : Service() {

    @Inject
    lateinit var taskManager: AiTaskManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 「停止」走通知栏的 action：一次 cancel 就够了，不需要再维持前台状态——
        // 任务被移除后 AiTaskManager 会把空快照同步给 host，由 host 停掉本服务。
        if (intent?.action == ACTION_CANCEL) {
            intent.getStringExtra(EXTRA_TASK_ID)?.let { taskId ->
                taskManager.cancel(taskId)
                Timber.i("从通知栏停止 AI 任务 %s", taskId)
            }
            return START_NOT_STICKY
        }

        startForeground(
            AiTaskNotifications.ONGOING_ID,
            AiTaskNotifications.buildOngoing(
                context = this,
                title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
                    .ifBlank { AiTaskNotifications.ongoingTitle(1) },
                text = intent?.getStringExtra(EXTRA_TEXT).orEmpty().ifBlank { "AI 正在生成…" },
                taskId = intent?.getStringExtra(EXTRA_TASK_ID).orEmpty()
            )
        )
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_UPDATE = "com.example.zhilu.action.AI_TASK_UPDATE"
        const val ACTION_CANCEL = "com.example.zhilu.action.AI_TASK_CANCEL"

        const val EXTRA_TITLE = "com.example.zhilu.extra.AI_TASK_TITLE"
        const val EXTRA_TEXT = "com.example.zhilu.extra.AI_TASK_TEXT"
        const val EXTRA_TASK_ID = "com.example.zhilu.extra.AI_TASK_ID"
    }
}
