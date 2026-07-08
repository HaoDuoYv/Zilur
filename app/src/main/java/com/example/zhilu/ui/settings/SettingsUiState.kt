package com.example.zhilu.ui.settings

data class SettingsUiState(
    val noteCount: Int = 0,
    val tagCount: Int = 0,
    val mediaCount: Int = 0,
    val totalMediaSize: Long = 0L,
    val notificationPermissionGranted: Boolean = true,
    val exportMessage: String? = null,
    val isWorking: Boolean = false,
    val error: String? = null
)
