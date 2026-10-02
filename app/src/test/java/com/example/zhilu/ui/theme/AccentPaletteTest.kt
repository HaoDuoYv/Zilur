package com.example.zhilu.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.zhilu.data.datastore.AccentColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 强调色板的设计约束。
 *
 * 这些数值是「换色时最容易悄悄塌掉」的地方：加第七个颜色时没人会重新手算对比度，
 * 而对比度不够的表现是「某些主按钮上的字看着发灰」——不容易被当成 bug 报上来。
 * 所以把门槛写死在这里，让加色的人当场撞墙。
 */
class AccentPaletteTest {

    /** WCAG AA 正文对比度门槛。低于此值的主色会让按钮上的字读不清。 */
    private val minContrast = 4.5

    @Test
    fun paletteHasSevenAccents() {
        assertEquals(7, AccentColor.entries.size)
    }

    @Test
    fun everyAccentDefinesBothSchemes() {
        AccentColor.entries.forEach { accent ->
            val paint = accentPaint(accent)
            assertTrue("$accent 缺少展示名", paint.label.isNotBlank())
            listOf("light" to paint.light, "dark" to paint.dark).forEach { (scheme, roles) ->
                assertTrue(
                    "$accent/$scheme 的主色不能是透明的",
                    roles.primary != Color.Transparent && roles.primary != Color.Unspecified
                )
                assertTrue(
                    "$accent/$scheme 的 onPrimary 不能是透明的",
                    roles.onPrimary != Color.Transparent && roles.onPrimary != Color.Unspecified
                )
                assertTrue(
                    "$accent/$scheme 的容器色不能是透明的",
                    roles.primaryContainer != Color.Transparent &&
                        roles.primaryContainer != Color.Unspecified
                )
                assertTrue(
                    "$accent/$scheme 的 onPrimaryContainer 不能是透明的",
                    roles.onPrimaryContainer != Color.Transparent &&
                        roles.onPrimaryContainer != Color.Unspecified
                )
            }
        }
    }

    @Test
    fun labelsAreDistinct() {
        val labels = AccentColor.entries.map { accentPaint(it).label }
        assertEquals(
            "每个强调色要有独立的展示名——它同时是无障碍播报用的描述",
            labels.size,
            labels.toSet().size
        )
    }

    @Test
    fun accentTextPairsAreReadableInBothSchemes() {
        AccentColor.entries.forEach { accent ->
            val paint = accentPaint(accent)
            assertPairReadable("$accent/light", paint.light.primary, paint.light.onPrimary)
            assertPairReadable(
                "$accent/light container",
                paint.light.primaryContainer,
                paint.light.onPrimaryContainer
            )
            assertPairReadable("$accent/dark", paint.dark.primary, paint.dark.onPrimary)
            assertPairReadable(
                "$accent/dark container",
                paint.dark.primaryContainer,
                paint.dark.onPrimaryContainer
            )
        }
    }

    @Test
    fun darkPrimaryIsBrightened() {
        // 深底上的强调元素要够亮才立得住；直接沿用浅色主色会让按钮暗成一团。
        AccentColor.entries.forEach { accent ->
            val paint = accentPaint(accent)
            assertTrue(
                "$accent 的深色主色应比浅色主色更亮",
                luminance(paint.dark.primary) > luminance(paint.light.primary)
            )
        }
    }

    @Test
    fun defaultAccentKeepsTheOriginalBrandBlue() {
        assertEquals(Color(0xFF3D4F6B), accentPaint(AccentColor.DEFAULT).light.primary)
    }

    @Test
    fun unknownRawValueFallsBackToDefault() {
        assertEquals(AccentColor.INK, AccentColor.fromRaw(null))
        assertEquals(AccentColor.INK, AccentColor.fromRaw(" UUID_NOT_A_COLOR "))
        assertEquals(AccentColor.VIOLET, AccentColor.fromRaw("VIOLET"))
    }

    private fun assertPairReadable(label: String, background: Color, foreground: Color) {
        val ratio = contrast(background, foreground)
        assertTrue(
            "$label 对比度不足：背景=$background 前景=$foreground 实测=${"%.2f".format(ratio)}",
            ratio >= minContrast
        )
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val brighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (brighter + 0.05) / (darker + 0.05)
    }

    /**
     * WCAG 相对亮度。
     *
     * 从 [Color.value] 直接拆 ARGB 分量，而不是读 `Color.red` 等取值器——
     * 那些取值器会做色彩空间转换，这里只需要盒内的 8 位 sRGB 原值。
     */
    private fun luminance(color: Color): Double {
        fun linear(channel: Int): Double {
            val c = channel / 255.0
            return if (c <= 0.03928f) c / 12.92 else java.lang.Math.pow((c + 0.055) / 1.055, 2.4)
        }
        // ARGB 在 value 的**高 32 位**（低 32 位留给色彩空间），所以必须先右移回来。
        // 并且要用 ushr：alpha 是 0xFF，落到 Int 里最高位为 1，`shr` 是**带符号**右移，
        // 位移时被符号位填满，拆出来的 RGB 会整体串位（症状是所有对比度都等于 1.00）。
        val argb = (color.value shr 32).toInt()
        val r = argb ushr 16 and 0xFF
        val g = argb ushr 8 and 0xFF
        val b = argb and 0xFF
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }
}
