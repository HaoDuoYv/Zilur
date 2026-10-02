package com.example.zhilu.common

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用前后台状态。
 *
 * 判据是唯一 Activity 的 started/stopped——不用 `ActivityManager.getRunningAppProcesses()`，
 * 那个接口在新版本上只能看到自己进程，判断不了「用户是否正看着应用」。
 *
 * 目前只有一个用途：AI 任务在**后台**结束时才补一条完成通知；
 * 用户就停留在会话页时结果已经直接呈现在眼前，再弹通知是打扰。
 */
@Singleton
class AppForegroundTracker @Inject constructor() {

    @Volatile
    var isForeground: Boolean = false
        private set

    fun onActivityStarted() {
        isForeground = true
    }

    fun onActivityStopped() {
        isForeground = false
    }
}
