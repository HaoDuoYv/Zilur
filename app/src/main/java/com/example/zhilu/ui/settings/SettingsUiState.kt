package com.example.zhilu.ui.settings

import com.example.zhilu.domain.model.AiSettings
import com.example.zhilu.data.datastore.AccentColor
import com.example.zhilu.data.datastore.ThemeMode
import com.example.zhilu.data.datastore.ThemePalette
import com.example.zhilu.domain.reminder.ReviewIntervals

data class SettingsUiState(
    val noteCount: Int = 0,
    val tagCount: Int = 0,
    val mediaCount: Int = 0,
    val totalMediaSize: Long = 0L,
    /** 「我的」身份卡：共记录过多少天 / 连续多少天。 */
    val recordedDays: Int = 0,
    val streakDays: Int = 0,
    val notificationPermissionGranted: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: AccentColor = AccentColor.DEFAULT,
    /** 配色外观（纸墨 / 动森）。与 [themeMode] 正交：外观决定长什么样，模式决定明暗。 */
    val themePalette: ThemePalette = ThemePalette.DEFAULT,
    /** 无障碍语义色板（§3.9）。 */
    val accessibleEmphasis: Boolean = false,
    val remindersEnabled: Boolean = true,
    /** 自定义复习间隔（天），见 `ReviewIntervals`。 */
    val reviewIntervals: List<Long> = ReviewIntervals.DEFAULT,
    val aiSettings: AiSettings = AiSettings.EMPTY,
    val aiTestInProgress: Boolean = false,
    val aiTestResult: String? = null,
    val exportMessage: String? = null,
    val isWorking: Boolean = false,
    val error: String? = null
)