package com.example.zhilu.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.zhilu.data.datastore.AccentColor
import com.example.zhilu.domain.model.EmphasisTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 语义角色与卡片身份色（设计文档 §3.2、§5.2）。
 *
 * 重点钉住两条容易退化的性质：
 * 1. 语义色**派生**自 `AccentPalette`，不是另抄一份 hex；
 * 2. 卡片身份色**不与任一语义角色撞值**——这是"一色一义"的底线。
 */
class EmphasisTonesTest {

    @Test
    fun `四个角色映射到四个不同的主题色`() {
        val accents = EmphasisTone.entries.map { it.accent }
        assertEquals(4, accents.size)
        assertEquals(accents.size, accents.toSet().size)
    }

    @Test
    fun `语义色直接等于对应主题色的 primary`() {
        for (tone in EmphasisTone.entries) {
            assertEquals(
                "浅色主题下 $tone 应等于 ${tone.accent} 的 primary",
                accentRoles(tone.accent, darkTheme = false).primary,
                emphasisToneColor(tone, darkTheme = false)
            )
            assertEquals(
                "深色主题下 $tone 应等于 ${tone.accent} 的 primary",
                accentRoles(tone.accent, darkTheme = true).primary,
                emphasisToneColor(tone, darkTheme = true)
            )
        }
    }

    @Test
    fun `角色命名是跨领域成立的四个中性词`() {
        assertEquals("要点", EmphasisTone.KEY.label)
        assertEquals("想法", EmphasisTone.IDEA.label)
        assertEquals("注意", EmphasisTone.WARN.label)
        assertEquals("待办", EmphasisTone.TODO.label)
    }

    @Test
    fun `角色字母与 fromCode 互逆`() {
        for (tone in EmphasisTone.entries) {
            assertEquals(tone, EmphasisTone.fromCode(tone.code))
        }
        assertEquals(null, EmphasisTone.fromCode('x'))
        assertEquals(null, EmphasisTone.fromCode('1'))
    }

    @Test
    fun `行内加深墨色四个互异且与块级色不同`() {
        val inks = EmphasisTone.entries.map { emphasisInkColor(it, darkTheme = false) }
        assertEquals(inks.size, inks.toSet().size)
        for (tone in EmphasisTone.entries) {
            assertNotEquals(
                "$tone 的行内色不应与块级色相同（相同则失去「小字加深」的意义）",
                emphasisToneColor(tone, darkTheme = false),
                emphasisInkColor(tone, darkTheme = false)
            )
        }
    }

    @Test
    fun `深色主题的行内色直接复用提亮后的 primary`() {
        for (tone in EmphasisTone.entries) {
            assertEquals(
                emphasisToneColor(tone, darkTheme = true),
                emphasisInkColor(tone, darkTheme = true)
            )
        }
    }

    // ---- 卡片身份色 ----

    @Test
    fun `轮转序有八色且互不相同`() {
        assertEquals(8, CardAccentRotation.size)
        assertEquals(8, CardAccentRotation.map { it.argb }.toSet().size)
    }

    @Test
    fun `卡片身份色不与任一语义角色撞值`() {
        // 四个语义角色的 light primary —— 语义色就是它们，身份色必须避开
        val semanticPrimaries = listOf(0xFF7C5C31, 0xFF4B6B4F, 0xFFC0392B, 0xFF3D6E6B).map { it.toInt() }
        for (accent in CardAccentRotation) {
            assertFalse(
                "身份色 ${accent.name}(${accent.argb.toUInt().toString(16)}) 与某个语义角色撞值了",
                accent.argb in semanticPrimaries
            )
        }
    }

    @Test
    fun `注意用的是正红而不是低饱和玫瑰`() {
        assertEquals(AccentColor.SCARLET, EmphasisTone.WARN.accent)
        assertEquals(Color(0xFFC0392B).toArgb(), emphasisToneColor(EmphasisTone.WARN, darkTheme = false).toArgb())
    }

    @Test
    fun `前五张卡覆盖五个不同色值`() {
        val firstFive = (0 until 5).map { cardAccentAt(it) }
        assertEquals(5, firstFive.toSet().size)
    }

    @Test
    fun `轮转会在第八张之后回到起点`() {
        assertEquals(cardAccentAt(0), cardAccentAt(8))
        assertEquals(cardAccentAt(1), cardAccentAt(9))
    }

    @Test
    fun `未设置时按序号回退到轮转色`() {
        for (index in 0 until 9) {
            assertEquals(
                Color(CardAccentRotation[index % CardAccentRotation.size].argb).toArgb(),
                cardAccentColor(stored = null, index = index, darkTheme = false).toArgb()
            )
        }
    }

    @Test
    fun `显式指定的身份色优先于轮转`() {
        val custom = 0xFF123456.toInt()
        val color = cardAccentColor(stored = custom, index = 0, darkTheme = false)
        assertNotEquals(cardAccentColor(stored = null, index = 0, darkTheme = false), color)
        assertTrue(color.alpha > 0f)
    }
}
