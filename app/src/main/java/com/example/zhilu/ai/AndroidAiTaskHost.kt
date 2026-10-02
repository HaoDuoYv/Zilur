package com.example.zhilu.ai

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.zhilu.common.AppForegroundTracker
import com.example.zhilu.domain.ai.model.AiTask
import com.example.zhilu.domain.ai.model.AiTaskState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * [AiTaskHost] 的 Android 实现：把任务快照翻译成前台服务的启停与通知。
 *
 * 两条规则：
 * - 有活跃任务 → 启动/刷新前台服务（进程保活 + 通知栏进度）；
 * - 没有活跃任务 → 停服务，并对**刚刚结束**的任务在应用处于后台时补一条完成通知。
 *   用户就停在会话页时结果已经扑面而来，再弹通知纯属打扰，所以按前后台分流。
 */
@Singleton
class AndroidAiTaskHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foregroundTracker: AppForegroundTracker
) : AiTaskHost {

    /** 上一次同步的活跃任务，用来识别「刚刚结束」的那一个。 */
    private var lastActive: List<AiTask> = emptyList()

    override fun sync(state: AiTaskState) {
        val active = state.activeTasks
        val finished = lastActive.filterNot { previous -> active.any { it.id == previous.id } }
        lastActive = active

        if (active.isEmpty()) {
            stopService()
            // 应用在前台时结果已经直接呈现在会话里，不再补通知。
            if (!foregroundTracker.isForeground) {
                finished.forEach { task -> AiTaskNotifications.notifyFinished(context, task) }
            }
            return
        }

        startService(active)
    }

    private fun startService(active: List<AiTask>) {
        val current = active.last()
        val intent = Intent(context, AiTaskService::class.java).apply {
            action = AiTaskService.ACTION_UPDATE
            putExtra(AiTaskService.EXTRA_TITLE, AiTaskNotifications.ongoingTitle(active.size))
            putExtra(AiTaskService.EXTRA_TEXT, AiTaskNotifications.ongoingText(current))
            putExtra(AiTaskService.EXTRA_TASK_ID, current.id)
        }
        // Android 12+ 禁止从后台启动前台服务。调用点都在前台交互里（用户点了发送），
        // 但真被拦下来也只影响通知保活，生成本身照跑，所以不把异常抛给上层。
        runCatching { ContextCompat.startForegroundService(context, intent) }
            .onFailure { Timber.w(it, "启动 AI 前台服务失败") }
    }

    private fun stopService() {
        runCatching { context.stopService(Intent(context, AiTaskService::class.java)) }
            .onFailure { Timber.w(it, "停止 AI 前台服务失败") }
    }
}
