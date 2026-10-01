package com.example.zhilu.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.zhilu.data.datastore.ThemeMode
import com.example.zhilu.ui.component.SegmentedToggle
import com.example.zhilu.ui.theme.Spacing

/** 外观：主题模式三段控件。 */
@Composable
fun AppearanceSection(
    themeMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    SettingsGroup(title = "外观") {
        SegmentedToggle(
            options = listOf(
                ThemeMode.SYSTEM to "跟随系统",
                ThemeMode.LIGHT to "浅色",
                ThemeMode.DARK to "深色"
            ),
            selected = themeMode,
            onSelect = onSelect,
            modifier = Modifier.padding(vertical = Spacing.Xs)
        )
    }
}