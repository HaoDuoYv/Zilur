package com.example.zhilu.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Light palette: warm paper with restrained indigo / ochre / olive accents.
// 纸面分层：页面底为暖纸，内容面为近白，凹陷面（搜索条/代码块）为微暗纸。
internal val PrimaryLight = Color(0xFF3D4F6B)
internal val OnPrimaryLight = Color(0xFFFFFFFF)
internal val PrimaryContainerLight = Color(0xFFE3E8F0)
internal val OnPrimaryContainerLight = Color(0xFF1E2A3D)

internal val SecondaryLight = Color(0xFF6B5B4A)
internal val OnSecondaryLight = Color(0xFFFFFFFF)
internal val SecondaryContainerLight = Color(0xFFE8E2DA)
internal val OnSecondaryContainerLight = Color(0xFF3D3328)

internal val TertiaryLight = Color(0xFF5A6650)
internal val OnTertiaryLight = Color(0xFFFFFFFF)
internal val TertiaryContainerLight = Color(0xFFE0E6D8)
internal val OnTertiaryContainerLight = Color(0xFF2F3628)

internal val BackgroundLight = Color(0xFFFAF8F3)   // Paper
internal val OnBackgroundLight = Color(0xFF191917) // InkStrong
internal val SurfaceLight = Color(0xFFFFFFFF)      // PaperRaised
internal val OnSurfaceLight = Color(0xFF191917)    // InkStrong
internal val SurfaceVariantLight = Color(0xFFF3F0E9) // PaperSunken
internal val OnSurfaceVariantLight = Color(0xFF77746C) // InkMuted

internal val OutlineLight = Color(0xFFC9C2B4)
internal val OutlineVariantLight = Color(0xFFE7E2D8) // Hairline

internal val ErrorLight = Color(0xFFB3261E)
internal val OnErrorLight = Color(0xFFFFFFFF)
internal val ErrorContainerLight = Color(0xFFF9DEDC)
internal val OnErrorContainerLight = Color(0xFF410E0B)

// Dark palette: deep ink charcoal with soft indigo / ochre / olive accents.
internal val PrimaryDark = Color(0xFFA8B8D2)
internal val OnPrimaryDark = Color(0xFF1E2A3D)
internal val PrimaryContainerDark = Color(0xFF2F3D53)
internal val OnPrimaryContainerDark = Color(0xFFD8DFEA)

internal val SecondaryDark = Color(0xFFC7B9A8)
internal val OnSecondaryDark = Color(0xFF3D3328)
internal val SecondaryContainerDark = Color(0xFF6B5B4A)
internal val OnSecondaryContainerDark = Color(0xFFE8E2DA)

internal val TertiaryDark = Color(0xFFB1BCA7)
internal val OnTertiaryDark = Color(0xFF2F3628)
internal val TertiaryContainerDark = Color(0xFF5A6650)
internal val OnTertiaryContainerDark = Color(0xFFE0E6D8)

internal val BackgroundDark = Color(0xFF141413)     // Paper
internal val OnBackgroundDark = Color(0xFFF2F0E9)   // InkStrong
internal val SurfaceDark = Color(0xFF1D1D1B)        // PaperRaised
internal val OnSurfaceDark = Color(0xFFF2F0E9)      // InkStrong
internal val SurfaceVariantDark = Color(0xFF262624)  // PaperSunken
internal val OnSurfaceVariantDark = Color(0xFFA8A49A) // InkMuted

internal val OutlineDark = Color(0xFF4A4843)
internal val OutlineVariantDark = Color(0xFF33322E) // Hairline

internal val ErrorDark = Color(0xFFF2B8B5)
internal val OnErrorDark = Color(0xFF601410)
internal val ErrorContainerDark = Color(0xFF8C1D18)
internal val OnErrorContainerDark = Color(0xFFF9DEDC)

// Editorial tag chips: muted, low-saturation hues aligned with the ink/paper theme.
val TagColors = listOf(
    0xFF6B5B8A.toInt(),
    0xFF4A5C7A.toInt(),
    0xFF4A6B5B.toInt(),
    0xFF8A6B3A.toInt(),
    0xFF8A4A5B.toInt(),
    0xFF4A7A7A.toInt(),
    0xFF6B6B6B.toInt()
)

// Fallback tag colour when a note carries no tag (replaces the littered 0xFF6B6B6B).
const val TagColorsNeutral = 0xFF6B6B6B.toInt()

/**
 * 深色主题下的标签色：把存储的低饱和深色按固定比例提亮，保证深底上的可读性。
 * 纯函数，便于测试与在 TagChip 中直接调用。
 */
fun darkTagColor(color: Int): Color = brighten(Color(color), factor = 0.42f)

@Suppress("MagicNumber")
private fun brighten(color: Color, factor: Float): Color {
    val r = color.red + (1f - color.red) * factor
    val g = color.green + (1f - color.green) * factor
    val b = color.blue + (1f - color.blue) * factor
    return Color(r, g, b, color.alpha)
}

// 搜索命中的高亮字色不再在这里落死——它就是当前强调色（`colorScheme.primary`），
// 由 [NoteRow] 在组合里取出后传给不可组合的 `highlightMatches`。
// 此前写死成主色 hex，导致换了强调色之后搜索结果里的命中词还是原来的墨蓝。

/**
 * 透明度层级：此前散落 alpha 的唯一来源。
 */
object AlphaTokens {
    const val Subtle = 0.08f      // divider background / 行内代码底
    const val Hover = 0.12f       // selected chip background
    const val Divider = 0.15f     // thin inner divider
    const val InsetLine = 0.06f   // 富文本 inset 细线
    const val CodeBg = 0.08f      // 行内代码底色
    const val DragTint = 0.25f    // 拖拽拾起底色
    const val Disabled = 0.38f    // disabled content
    const val Border = 0.5f       // regular border / insert line
    const val Overlay = 0.55f     // branch background overlay
    const val Muted = 0.6f        // empty state / de-emphasized prompt
    const val Hint = 0.7f         // placeholder / hint text
    const val SecondaryText = 0.85f // 次级正文
    const val Scrim = 0.32f       // sheet / dialog 遮罩

    /** 行内语义标记的荧光笔底色。 */
    const val InlineMark = 0.20f

    /**
     * 块级语义标记（L1）的淡底。
     *
     * 与 [Subtle] 同值但**语义不同**，因此单独命名：`Subtle` 是中性墨的透明度层级，
     * 这里是"语义色铺底"，将来任何一方要调都不会牵动另一方。
     */
    const val EmphasisWash = 0.08f

    /**
     * 编辑态焦点块的底色。
     *
     * 刻意比 [EmphasisWash] 更淡：焦点底是 4%、语义标记底是 8%，
     * 两者若同值，"正在编辑的块"和"已标记的块"就分不出来了。
     */
    const val BlockFocus = 0.04f
}

/**
 * M3 ColorScheme 未覆盖的纸墨语义色（正文层级、微弱墨、遮罩、暖阴影）。
 * 通过 [LocalExtendedColors] 随主题下发，避免组件里散落 hard-coded Color(0x…)。
 */
@Immutable
data class ExtendedColors(
    val inkBody: Color,
    val inkFaint: Color,
    val hairline: Color,
    val scrim: Color,
    val shadowInk: Color,
    val isDark: Boolean
)

internal val LightExtendedColors = ExtendedColors(
    inkBody = Color(0xFF3B3A36),
    inkFaint = Color(0xFF9A968C),
    hairline = OutlineVariantLight,
    scrim = Color(0x52000000),
    shadowInk = Color(0x1A221E16),
    isDark = false
)

internal val DarkExtendedColors = ExtendedColors(
    inkBody = Color(0xFFD8D5CC),
    inkFaint = Color(0xFF7C7972),
    hairline = OutlineVariantDark,
    scrim = Color(0x52000000),
    shadowInk = Color(0x73000000),
    isDark = true
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }