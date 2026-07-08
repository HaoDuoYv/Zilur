package com.example.zhilu.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionStateTest {
    @Test
    fun sdkBelow33DoesNotRequirePostNotificationsRuntimePermission() {
        assertFalse(NotificationPermissionState.requiresRuntimePermission(32))
    }

    @Test
    fun sdk33RequiresPostNotificationsRuntimePermission() {
        assertTrue(NotificationPermissionState.requiresRuntimePermission(33))
    }
}
