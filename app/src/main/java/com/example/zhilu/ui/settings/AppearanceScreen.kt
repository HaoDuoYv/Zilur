package com.example.zhilu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.data.datastore.ThemeMode
import com.example.zhilu.domain.model.AiSettings
import com.example.zhilu.data.datastore.ThemePalette
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.navigation.AppTabScaffold
import com.example.zhilu.ui.theme.LocalExtendedColors
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.paletteLabel

/**
 * 「外观」独立页。
 *
 * 为什么从「我的」里搬出来：外观项已经长到三组（配色方案 / 明暗 / 强调色 + 无障碍色板），
 * 混在设置长列表里既难找、也没空间放配色缩略卡。搬出来之后：
 * - 「我的」里只留一行入口，**行尾直接写出当前外观名**，不用进去就知道现在是什么；
 * - 这一页可以专心把"选哪个"讲清楚（缩略卡 + 一句话说明），而不只是给几个开关。
 */
@Composable
fun AppearanceScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val darkTheme = LocalExtendedColors.current.isDark

    AppTabScaffold(
        topBar = {
            AppTopBar(
                title = "外观",
                onBack = { navController.popBackStack() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(bottom = Spacing.Xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.Xs)
        ) {
            SettingsGroup(title = "配色方案") {
                PalettePicker(
                    selected = state.themePalette,
                    onSelect = viewModel::setThemePalette,
                    darkTheme = darkTheme
                )
            }

            AppearanceSection(
                themeMode = state.themeMode,
                accentColor = state.accentColor,
                accessibleEmphasis = state.accessibleEmphasis,
                onSelectThemeMode = viewModel::setThemeMode,
                onSelectAccent = viewModel::setAccentColor,
                onToggleAccessibleEmphasis = viewModel::setAccessibleEmphasis
            )
        }
    }
}

/** 「我的」里那一行入口的说明文字：把当前外观与明暗带出来，不进去也知道现在是什么。 */
fun appearanceSummary(palette: ThemePalette, themeMode: ThemeMode): String {
    val mode = when (themeMode) {
        ThemeMode.SYSTEM -> "跟随系统"
        ThemeMode.LIGHT -> "浅色"
        ThemeMode.DARK -> "深色"
    }
    return "${paletteLabel(palette)} · $mode"
}

/**
 * AI 配置入口那一行的**行尾摘要**：**当前用哪个 AI** —— 这是进去之前最想知道的事。
 *
 * 现在这一行是「标题 + 行尾值」而不是「标题 + 第二行描述」（见 `SettingsRowValue`），
 * 所以只给短值，长句留给进去之后的页面讲：
 * - 没有服务 → 「未接入」；
 * - 有服务但都不可用（停用 / 没填全）→ 「无可用的」，否则用户会以为只是没选；
 * - 正常 → 当前服务的显示名。
 */
fun aiConfigSummary(settings: AiSettings): String {
    if (settings.services.isEmpty()) return "未接入"
    val active = settings.resolveActive() ?: return "无可用的"
    return active.displayName
}
