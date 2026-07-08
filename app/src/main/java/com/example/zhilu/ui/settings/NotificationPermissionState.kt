package com.example.zhilu.ui.settings

object NotificationPermissionState {
    fun requiresRuntimePermission(sdkInt: Int): Boolean = sdkInt >= 33
}
