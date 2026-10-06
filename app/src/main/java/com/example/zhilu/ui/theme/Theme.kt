package com.example.zhilu.ui.theme

import android.app.Activity
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.zhilu.data.datastore.AccentColor
import com.example.zhilu.data.datastore.ThemeMode
import com.example.zhilu.data.datastore.ThemePalette

/**
 * 应用主题。
 *
 * 三个正交维度，**别把它们混成一个开关**：
 * - [palette]：外观（纸墨 / 动森）—— 决定"长什么样"：配色、圆角、卡片身份色、标签色；
 * - [themeMode]：明暗（跟随系统 / 浅色 / 深色）—— 每种外观都自带浅深两套方案；
 * - [accentColor]：强调色 —— 同一个键在两套外观下取到不同色值（墨蓝 ↔ 叶绿），
 *   所以切换外观**不会丢**用户已选的档位。
 *
 * 其余角色（背景 / 卡面 / 描边 / 语义中性色）成套写在 `ThemePalettes.kt`，
 * 这里只负责挑一套、再把强调色盖上去。
 */
@Composable
fun ZhiLuTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColor: AccentColor = AccentColor.DEFAULT,
    palette: ThemePalette = ThemePalette.DEFAULT,
    // Disabled by default to preserve the editorial brand palette across devices.
    dynamicColor: Boolean = false,
    /** 无障碍语义色板（§3.9）：开启后四个语义角色换成色盲友好 + 明度分级的一套。 */
    accessibleEmphasis: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val paint = palettePaint(palette)
    val colorScheme = when {
        // Dynamic color is intentionally not used by default. To opt in on Android 12+,
        // import dynamicLightColorScheme / dynamicDarkColorScheme and pass true here.
        dynamicColor -> paint.scheme(darkTheme)
        else -> paint.scheme(darkTheme)
    }.withAccent(accentColor = accentColor, darkTheme = darkTheme, palette = palette)

    val view = LocalView.current
    val context = LocalContext.current
    val reducedMotion = remember { context.isReduceMotionEnabled() }
    val extendedColors = paint.extended(darkTheme)
    if (!view.isInEditMode) {
        SideEffect {
            // 系统栏颜色由内容透明绘制（edge-to-edge）；仅切换图标明暗外观。
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalReducedMotion provides reducedMotion,
        LocalExtendedColors provides extendedColors,
        LocalAccessibleEmphasis provides accessibleEmphasis,
        // 色板随主题下发：卡片身份色、标签胶囊、设置页色板都要按当前外观取色，
        // 这些取值点散落在各个组件里，逐层传参会把签名污染一大片。
        LocalThemePalette provides palette
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            // 圆角也跟着外观走：动森那套整体放大，"一切都是圆的"是它观感的一半。
            shapes = paint.shapes,
            content = content
        )
    }
}

/**
 * 把用户选的强调色盖到基线方案上。
 *
 * 只替换 `primary` 这一族（含各自的 `on*`）：它是 M3 里「应用强调色」的落点，
 * FAB、进度条、开关、选中态、顶部操作都挂在它上面。
 * 其余角色保持当前外观的基线不变，否则整个界面会被染成一个颜色。
 *
 * 注意 [dynamicColor] 分支也走这里：动态色生效时用户选的强调色优先，
 * 否则设置里的选择会神秘失效。
 *
 * 标 `internal` 而不是 `private` **是为了能被单测直接调用**：它是"外观 → 实际渲染"
 * 这一段的关键接线，只测色板数据证明不了"主题真的用了这份色板"。
 */
internal fun ColorScheme.withAccent(
    accentColor: AccentColor,
    darkTheme: Boolean,
    palette: ThemePalette
): ColorScheme {
    val roles = accentRoles(accent = accentColor, darkTheme = darkTheme, palette = palette)
    return copy(
        primary = roles.primary,
        onPrimary = roles.onPrimary,
        primaryContainer = roles.primaryContainer,
        onPrimaryContainer = roles.onPrimaryContainer
    )
}

/** 当前外观。默认纸墨，这样忘了下发时也不会渲染成半套颜色。 */
val LocalThemePalette = androidx.compose.runtime.staticCompositionLocalOf {
    ThemePalette.DEFAULT
}

private fun Context.isReduceMotionEnabled(): Boolean {
    return try {
        val animatorScale = Settings.Global.getFloat(
            contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        val transitionScale = Settings.Global.getFloat(
            contentResolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f
        )
        animatorScale == 0f || transitionScale == 0f
    } catch (_: Exception) {
        false
    }
}
