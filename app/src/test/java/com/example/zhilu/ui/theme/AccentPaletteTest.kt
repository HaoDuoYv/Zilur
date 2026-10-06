package com.example.zhilu.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.zhilu.data.datastore.AccentColor
import com.example.zhilu.data.datastore.ThemePalette
import com.example.zhilu.domain.model.EmphasisTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 配色方案的设计约束。
 *
 * 这些数值是「换色时最容易悄悄塌掉」的地方：加第七个颜色时没人会重新手算对比度，
 * 而对比度不够的表现是「某些主按钮上的字看着发灰」——不容易被当成 bug 报上来。
 * 所以把门槛写死在这里，让加色的人当场撞墙。
 *
 * **覆盖两套外观**（纸墨 / 动森）：动森那套的 `on*` 是跟着新底色重新配的，
 * 正是最需要被这几条卡住的地方。
 */
class AccentPaletteTest {

    /** WCAG AA 正文对比度门槛。低于此值的主色会让按钮上的字读不清。 */
    private val minContrast = 4.5

    @Test
    fun paletteHasEightAccents() {
        // 7 → 8：新增「正红」。追加在枚举末尾，避免打乱既有用户按 name 持久化的值。
        assertEquals(8, AccentColor.entries.size)
    }

    @Test
    fun everyAccentDefinesBothSchemes() {
        ThemePalette.entries.forEach { palette ->
            AccentColor.entries.forEach { accent ->
                val paint = palettePaint(palette).accent(accent)
                assertTrue("$palette/$accent 缺少展示名", paint.label.isNotBlank())
                listOf("light" to paint.light, "dark" to paint.dark).forEach { (scheme, roles) ->
                    assertTrue(
                        "$palette/$accent/$scheme 的主色不能是透明的",
                        roles.primary != Color.Transparent && roles.primary != Color.Unspecified
                    )
                    assertTrue(
                        "$palette/$accent/$scheme 的 onPrimary 不能是透明的",
                        roles.onPrimary != Color.Transparent && roles.onPrimary != Color.Unspecified
                    )
                    assertTrue(
                        "$palette/$accent/$scheme 的容器色不能是透明的",
                        roles.primaryContainer != Color.Transparent &&
                            roles.primaryContainer != Color.Unspecified
                    )
                    assertTrue(
                        "$palette/$accent/$scheme 的 onPrimaryContainer 不能是透明的",
                        roles.onPrimaryContainer != Color.Transparent &&
                            roles.onPrimaryContainer != Color.Unspecified
                    )
                }
            }
        }
    }

    @Test
    fun labelsAreDistinct() {
        ThemePalette.entries.forEach { palette ->
            val labels = AccentColor.entries.map { palettePaint(palette).accent(it).label }
            assertEquals(
                "$palette 每个强调色要有独立的展示名——它同时是无障碍播报用的描述",
                labels.size,
                labels.toSet().size
            )
        }
    }

    @Test
    fun accentTextPairsAreReadableInBothSchemes() {
        ThemePalette.entries.forEach { palette ->
            AccentColor.entries.forEach { accent ->
                val paint = palettePaint(palette).accent(accent)
                assertPairReadable("$palette/$accent/light", paint.light.primary, paint.light.onPrimary)
                assertPairReadable(
                    "$palette/$accent/light container",
                    paint.light.primaryContainer,
                    paint.light.onPrimaryContainer
                )
                assertPairReadable("$palette/$accent/dark", paint.dark.primary, paint.dark.onPrimary)
                assertPairReadable(
                    "$palette/$accent/dark container",
                    paint.dark.primaryContainer,
                    paint.dark.onPrimaryContainer
                )
            }
        }
    }

    @Test
    fun darkPrimaryIsBrightened() {
        // 深底上的强调元素要够亮才立得住；直接沿用浅色主色会让按钮暗成一团。
        ThemePalette.entries.forEach { palette ->
            AccentColor.entries.forEach { accent ->
                val paint = palettePaint(palette).accent(accent)
                assertTrue(
                    "$palette/$accent 的深色主色应比浅色主色更亮",
                    luminance(paint.dark.primary) > luminance(paint.light.primary)
                )
            }
        }
    }

    @Test
    fun defaultAccentKeepsTheOriginalBrandBlue() {
        // 纸墨的出厂强调色必须保持不变：它是最初的纸墨主题主色，改了等于换品牌色。
        assertEquals(Color(0xFF3D4F6B), paperInkAccentPaint(AccentColor.DEFAULT).light.primary)
    }

    @Test
    fun animalIslandAccentDiffersFromPaperInk() {
        // 同一个键在两套外观下必须是不同色值，否则"切了外观但强调色没变"。
        AccentColor.entries.forEach { accent ->
            assertNotEquals(
                "「${accent.name}」在两套外观下应当取不同色值",
                paperInkAccentPaint(accent).light.primary,
                palettePaint(ThemePalette.ANIMAL_ISLAND).accent(accent).light.primary
            )
        }
    }

    /**
     * `ZhiLuTheme` 的接线：外观 → 实际渲染用的 `ColorScheme`。
     *
     * 只测色板数据证明不了"主题真的用了这份色板"——值定义得好好的、`ZhiLuTheme`
     * 却漏传 `palette`，两边都不会红，而用户看到的是"切了没反应"。这里把接线钉死。
     */
    @Test
    fun accentOverridesThePalettePrimary() {
        listOf(ThemePalette.PAPER_INK, ThemePalette.ANIMAL_ISLAND).forEach { palette ->
            listOf(true, false).forEach { dark ->
                val base = palettePaint(palette).scheme(dark)
                val applied = base.withAccent(AccentColor.VIOLET, dark, palette)
                val expected = palettePaint(palette).accent(AccentColor.VIOLET)

                assertEquals(
                    "$palette/${if (dark) "dark" else "light"} 的 primary 应当来自强调色",
                    if (dark) expected.dark.primary else expected.light.primary,
                    applied.primary
                )
                assertEquals(
                    "onPrimary 也要一起换，否则按钮上的字会失去对比",
                    if (dark) expected.dark.onPrimary else expected.light.onPrimary,
                    applied.onPrimary
                )
            }
        }
    }

    @Test
    fun accentLeavesOtherRolesUntouched() {
        // 只换 primary 一族：背景/卡面/描边不能被强调色染掉，否则"纸墨的中性底子"就没了
        val base = palettePaint(ThemePalette.PAPER_INK).light
        val applied = base.withAccent(AccentColor.CRIMSON, darkTheme = false, palette = ThemePalette.PAPER_INK)

        assertEquals(base.background, applied.background)
        assertEquals(base.surface, applied.surface)
        assertEquals(base.secondary, applied.secondary)
        assertEquals(base.outline, applied.outline)
    }

    @Test
    fun switchingPaletteChangesTheRenderedPrimary() {
        // 同一个强调色档位 + 同一明暗，两套外观必须渲染出不同的 primary
        listOf(true, false).forEach { dark ->
            val paper = palettePaint(ThemePalette.PAPER_INK).scheme(dark)
                .withAccent(AccentColor.INK, dark, ThemePalette.PAPER_INK)
            val animal = palettePaint(ThemePalette.ANIMAL_ISLAND).scheme(dark)
                .withAccent(AccentColor.INK, dark, ThemePalette.ANIMAL_ISLAND)

            assertNotEquals(
                "${if (dark) "深色" else "浅色"}下切换外观必须改变 primary",
                paper.primary,
                animal.primary
            )
        }
    }

    /**
     * 语义角色的**小标记色必须看得见**。
     *
     * 这一条是抓真 bug 抓出来的：动森初版的「要点」直接沿用 accent 的「蜂蜜」
     * `#D9A441` —— 当按钮填充配深字很好看（6.16），但当色条压在奶油底上只有 **2.08**，
     * 等于"色条消失了"（对照纸墨是 5.67）。所以小标记另有一档更暗的色
     * （[PalettePaint.toneBlocks]）。
     *
     * 门槛 3.0 而不是 4.5：它是**图形**（色条/圆点），不是文字。WCAG 对非文本内容
     * 的要求就是 3:1。
     */
    @Test
    fun toneBlockColorsAreVisibleOnEverySurface() {
        ThemePalette.entries.forEach { palette ->
            val paint = palettePaint(palette)
            listOf(true, false).forEach { dark ->
                val scheme = paint.scheme(dark)
                val label = "$palette/${if (dark) "dark" else "light"}"
                val surfaces = listOf(
                    "背景" to scheme.background,
                    "卡面" to scheme.surface,
                    "凹陷面" to scheme.surfaceVariant
                )
                EmphasisTone.entries.forEach { tone ->
                    val mark = paint.toneBlock(tone, dark)
                    surfaces.forEach { (surfaceName, surface) ->
                        val ratio = contrastRatio(mark, surface)
                        assertTrue(
                            "$label ${tone.label} 标记色在${surfaceName}上看不清：" +
                                "标记=$mark 底=$surface 实测=${"%.2f".format(ratio)}（需 >= 3.0）",
                            ratio >= 3.0
                        )
                    }
                }
            }
        }
    }

    @Test
    fun animalIslandToneBlocksDifferFromPaperInk() {
        // 语义标记也要跟着外观换，否则动森下还是纸墨那四个色
        EmphasisTone.entries.forEach { tone ->
            assertNotEquals(
                "「${tone.label}」在两套外观下的标记色应当不同",
                palettePaint(ThemePalette.PAPER_INK).toneBlock(tone, darkTheme = false),
                palettePaint(ThemePalette.ANIMAL_ISLAND).toneBlock(tone, darkTheme = false)
            )
        }
    }

    /**
     * 压在实心色块上的文字必须读得清 —— 两套外观 × 浅深 × 三种填充都要过。
     *
     * 这一条抓到了真 bug：gutter 的激活序号原本写死 `Color.White`，
     * 深色模式下主色是**提亮后**的浅色，白字实测只有 **1.68**（动森嫩叶绿）/ 2.01（纸墨浅墨蓝）、
     * 身份色圆上 2.64。浅色模式下这些恰好都是白，所以缺陷只在深色模式露出来。
     *
     * **门槛是 3.0 而不是 4.5**，理由不是"想让测试过"：
     * 中间亮度的填充（`Color.luminance()` 口径下 0.35~0.50）两种字色都到不了 4.5 ——
     * 最多 3.0~3.4，这是"中等明度填充"的物理天花板，换字色解决不了。
     * 而这两处都是**短标签**（一位数序号 / 单个汉字），按 WCAG 大字号标准 3.0 即达标。
     * 要更高只能改填充色本身，那是设计取舍，不该由这条测试偷偷决定。
     */
    @Test
    fun inkOnFilledShapesIsReadable() {
        ThemePalette.entries.forEach { palette ->
            val paint = palettePaint(palette)
            listOf(true, false).forEach { dark ->
                val label = "$palette/${if (dark) "dark" else "light"}"
                val scheme = paint.scheme(dark)

                // 填充一：卡片身份色圆（gutter 激活态、身份徽标）
                paint.cardAccents.forEachIndexed { index, argb ->
                    val fill = if (dark) darkTagColor(argb) else Color(argb)
                    assertInkReadable("$label 身份色#$index", fill)
                }

                // 填充二：语义角色的小标记圆（行内工具条选中态）
                EmphasisTone.entries.forEach { tone ->
                    assertInkReadable("$label ${tone.label}标记", paint.toneBlock(tone, dark))
                }

                // 填充三：四个实心主色（按钮 / FAB）—— 这一组有配套的 on* 角色，
                // 门槛按正文 4.5 卡，比上面两处严。
                listOf("primary", "secondary", "tertiary", "error").forEach { role ->
                    val fill = when (role) {
                        "primary" -> scheme.primary
                        "secondary" -> scheme.secondary
                        "tertiary" -> scheme.tertiary
                        else -> scheme.error
                    }
                    val on = when (role) {
                        "primary" -> scheme.onPrimary
                        "secondary" -> scheme.onSecondary
                        "tertiary" -> scheme.onTertiary
                        else -> scheme.onError
                    }
                    val ratio = contrastRatio(fill, on)
                    // 把实测值一并报出来：调色时不用再另写脚本量一遍
                    assertTrue(
                        "$label $role on$role 实测=${"%.2f".format(ratio)}（需 >= 4.5）" +
                            " 候选深字=${"%.2f".format(contrastRatio(fill, Color(0xFF1A1A1A)))}" +
                            " 候选白字=${"%.2f".format(contrastRatio(fill, Color.White))}",
                        ratio >= 4.5
                    )
                }
            }
        }
    }

    /**
     * `on*` 角色的推荐取值（把实测结果打出来）。
     *
     * 不做断言，作用是**给出可复制的数值**：调色时不用另写脚本量一遍、
     * 也不用猜"白字还是深字"。真正的守卫是 [inkOnFilledShapesIsReadable]。
     */
    @Test
    fun printRecommendedOnColors() {
        val candidates = listOf(Color.White, Color(0xFF1A1A1A), Color(0xFF241A0F))
        ThemePalette.entries.forEach { palette ->
            val paint = palettePaint(palette)
            listOf(true, false).forEach { dark ->
                val scheme = paint.scheme(dark)
                val label = "$palette/${if (dark) "dark" else "light"}"
                listOf("primary", "secondary", "tertiary", "error").forEach { role ->
                    val fill = when (role) {
                        "primary" -> scheme.primary
                        "secondary" -> scheme.secondary
                        "tertiary" -> scheme.tertiary
                        else -> scheme.error
                    }
                    val on = when (role) {
                        "primary" -> scheme.onPrimary
                        "secondary" -> scheme.onSecondary
                        "tertiary" -> scheme.onTertiary
                        else -> scheme.onError
                    }
                    val best = bestInkOn(fill, candidates)
                    println(
                        "ONCOLOR $label $role fill=${fill.toHex()} " +
                            "current=${"%.2f".format(contrastRatio(fill, on))} " +
                            "best=${best.toHex()}(${"%.2f".format(contrastRatio(fill, best))})"
                    )
                }
            }
        }
    }

    private fun Color.toHex(): String =
        "%06X".format((value shr 32).toInt() and 0xFFFFFF)

    private fun assertInkReadable(label: String, fill: Color) {
        val ink = inkOnFill(fill)
        val ratio = contrastRatio(fill, ink)
        assertTrue(
            "$label 上的文字读不清：填充=$fill 文字=$ink 实测=${"%.2f".format(ratio)}（需 >= 3.0）",
            ratio >= 3.0
        )
    }

    @Test
    fun unknownRawValueFallsBackToDefault() {
        assertEquals(AccentColor.INK, AccentColor.fromRaw(null))
        assertEquals(AccentColor.INK, AccentColor.fromRaw(" UUID_NOT_A_COLOR "))
        assertEquals(AccentColor.VIOLET, AccentColor.fromRaw("VIOLET"))
    }

    @Test
    fun unknownPaletteFallsBackToPaperInk() {
        assertEquals(ThemePalette.PAPER_INK, ThemePalette.fromRaw(null))
        assertEquals(ThemePalette.PAPER_INK, ThemePalette.fromRaw("NOT_A_PALETTE"))
        assertEquals(ThemePalette.ANIMAL_ISLAND, ThemePalette.fromRaw("ANIMAL_ISLAND"))
    }

    /**
     * 基底色（背景 / 卡面 / 正文）的对比度。
     *
     * 比强调色更容易被忽略，但它影响的是**整屏**可读性：动森那套换了奶油底和
     * 偏绿的深色底，正文色是跟着重配的，必须一起卡住。
     *
     * **纸墨那套允许一个已存在的例外**，见 [acceptedShortfalls]：它比动森早得多，
     * 当年没有这层守卫，现在按 4.5 卡会要求改既有观感（把墨色压深一档），
     * 而这不是本次改动该顺手做的事。例外**逐条列出**而不是整体放行——这样新增外观
     * 或新增角色时不会被漏过去。
     */
    @Test
    fun baseSurfacesAreReadableInBothSchemes() {
        ThemePalette.entries.forEach { palette ->
            listOf(true, false).forEach { dark ->
                val scheme = palettePaint(palette).scheme(dark)
                val label = "$palette/${if (dark) "dark" else "light"}"
                assertPairReadable("$label 正文/背景", scheme.background, scheme.onBackground)
                assertPairReadable("$label 正文/卡面", scheme.surface, scheme.onSurface)
                assertPairReadable(
                    "$label 次级文字/凹陷面",
                    scheme.surfaceVariant,
                    scheme.onSurfaceVariant,
                    accepted = acceptedShortfalls.contains("$label 次级文字/凹陷面")
                )
            }
        }
    }

    /**
     * 已知且**刻意保留**的对比度不足项。
     *
     * 唯一一条是纸墨浅色的次级文字（`#77746C` on `#F3F0E9`，实测 4.10）。
     * 它是纸墨出厂配色的一部分，压深一档会改动既有观感；要动请单独开一次
     * "提高纸墨对比度"的改动，而不是混在外观功能里悄悄改掉。
     */
    private val acceptedShortfalls = setOf(
        "PAPER_INK/light 次级文字/凹陷面"
    )

    private fun assertPairReadable(
        label: String,
        background: Color,
        foreground: Color,
        accepted: Boolean = false
    ) {
        val ratio = contrastRatio(background, foreground)
        if (accepted) return
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
