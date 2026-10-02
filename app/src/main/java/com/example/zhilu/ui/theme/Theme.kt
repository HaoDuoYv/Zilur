package com.example.zhilu.ui.theme

import android.app.Activity
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.zhilu.data.datastore.AccentColor
import com.example.zhilu.data.datastore.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

@Composable
fun ZhiLuTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColor: AccentColor = AccentColor.INK,
    // Disabled by default to preserve the editorial brand palette across devices.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = when {
        dynamicColor -> {
            // Dynamic color is intentionally not used by default. To opt in on Android 12+,
            // import dynamicLightColorScheme / dynamicDarkColorScheme and pass true here.
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }.withAccent(accentColor = accentColor, darkTheme = darkTheme)

    val view = LocalView.current
    val context = LocalContext.current
    val reducedMotion = remember { context.isReduceMotionEnabled() }
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
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
        LocalExtendedColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}

/**
 * 把用户选的强调色盖到基线方案上。
 *
 * 只替换 `primary` 这一族（含各自的 `on*`）：它是 M3 里「应用强调色」的落点，
 * FAB、进度条、开关、选中态、顶部操作都挂在它上面。
 * 其余角色保持纸墨基线不变——见 [accentPaint] 关于为什么不整体换色的说明。
 *
 * 注意 [dynamicColor] 分支也走这里：动态色生效时用户选的强调色优先，
 * 否则设置里的选择会神秘失效。
 */
private fun ColorScheme.withAccent(accentColor: AccentColor, darkTheme: Boolean): ColorScheme {
    val roles = accentRoles(accent = accentColor, darkTheme = darkTheme)
    return copy(
        primary = roles.primary,
        onPrimary = roles.onPrimary,
        primaryContainer = roles.primaryContainer,
        onPrimaryContainer = roles.onPrimaryContainer
    )
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
