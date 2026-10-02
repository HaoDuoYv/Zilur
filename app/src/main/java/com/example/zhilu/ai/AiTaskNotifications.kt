package com.example.zhilu.ai

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
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
import com.example.zhilu.domain.ai.model.AiTask
import com.example.zhilu.domain.ai.model.AiTaskPhase

/**
 * AI 任务通知（进行中 / 已完成）的唯一构建入口。
 *
 * 前台服务与「任务结束后补发通知」两条路径共用同一份 channel 与文案拼装，
 * 避免两边各写一套、以后改文案漏掉一半。
 */
internal object AiTaskNotifications {

    const val CHANNEL_ID = "zhilu_ai_tasks"

    /** 进行中通知：由前台服务持有，任务全部结束即随服务一起消失。 */
    const val ONGOING_ID = 4101

    /** 完成通知：一次性，点开即消。 */
    const val COMPLETION_ID = 4102

    private const val CHANNEL_NAME = "AI 生成"
    private const val REQUEST_OPEN_ASSISTANT = 4110
    private const val REQUEST_CANCEL = 4111

    private val WHITESPACE = Regex("\\s+")

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            // LOW：不发声不震动。它是一条「正在进行」的状态，和提醒（DEFAULT）不是一类东西。
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "AI 助手生成进度"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * 前台服务的常驻通知。文案在 [AndroidAiTaskHost] 侧拼好后经 Intent 传进来——
     * 服务本身只负责显示，不做业务判断。
     */
    fun buildOngoing(
        context: Context,
        title: String,
        text: String,
        taskId: String
    ): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAssistantIntent(context))
            .addAction(0, "停止", cancelIntent(context, taskId))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** 任务在后台结束时补发的通知。返回 false 表示没有通知权限，什么都没发。 */
    @SuppressLint("MissingPermission")
    fun notifyFinished(context: Context, task: AiTask): Boolean {
        ensureChannel(context)
        if (!canPostNotifications(context)) return false

        val failed = task.phase == AiTaskPhase.FAILED
        val body = finishedBody(task)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (failed) "AI 生成失败" else "AI 生成完成")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAssistantIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(COMPLETION_ID, notification)
            true
        }.getOrDefault(false)
    }

    fun ongoingTitle(activeCount: Int): String =
        if (activeCount > 1) "知录 · $activeCount 个 AI 任务进行中" else "知录 · AI 正在生成"

    fun ongoingText(task: AiTask): String = when {
        task.phase == AiTaskPhase.TOOL_CALLING && task.toolName != null ->
            "正在调用 ${toolNameLabel(task.toolName)}…"
        task.streamText.isNotBlank() ->
            task.streamText.trim().replace(WHITESPACE, " ").takeLast(60)
        else -> "AI 正在生成…"
    }

    private fun finishedBody(task: AiTask): String = task.error?.takeIf { it.isNotBlank() }
        ?: task.streamText.trim().replace(WHITESPACE, " ").take(90).ifBlank { "点击查看结果" }

    private fun openAssistantIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_ASSISTANT
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ASSISTANT, true)
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_ASSISTANT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelIntent(context: Context, taskId: String): PendingIntent {
        val intent = Intent(context, AiTaskService::class.java).apply {
            action = AiTaskService.ACTION_CANCEL
            putExtra(AiTaskService.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getService(
            context,
            REQUEST_CANCEL,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    const val EXTRA_OPEN_ASSISTANT = "com.example.zhilu.extra.OPEN_ASSISTANT"
    private const val ACTION_OPEN_ASSISTANT = "com.example.zhilu.action.OPEN_ASSISTANT"
}
