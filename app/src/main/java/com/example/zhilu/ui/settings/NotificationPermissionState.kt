package com.example.zhilu.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 13（API 33）起 `POST_NOTIFICATIONS` 变成运行时权限：不申请的话，
 * 提醒、AI 任务完成通知都会**静默丢弃**（系统不报错，用户就是收不到）。
 */
object NotificationPermissionState {
    fun requiresRuntimePermission(sdkInt: Int): Boolean = sdkInt >= 33
}

/**
 * 当前是否**还需要**弹一次通知权限申请。
 *
 * 抽到 [NotificationPermissionState] 旁边（而不是留在 `NoteEditScreen` 里当私有扩展），
 * 是因为它是"申请前先问一句"的通用判断：提醒中心、待办提醒、AI 后台通知都会问同一个问题，
 * 留在某个屏幕文件里会让下一个调用点只能复制一遍。
 */
fun Context.shouldRequestNotificationPermission(): Boolean =
    NotificationPermissionState.requiresRuntimePermission(Build.VERSION.SDK_INT) &&
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
