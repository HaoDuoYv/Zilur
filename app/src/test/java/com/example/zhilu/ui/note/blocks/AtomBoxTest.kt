package com.example.zhilu.ui.note.blocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 公式图绘制尺寸的规则测试。
 *
 * 这里验的不是"数值好不好看"，而是**那些踩过的坑不会再回来**：
 *
 * - 图绝不会被放大（放大 = 同一公式在编辑态与只读态两个大小）；
 * - 图绝不会越出"该行剩余宽度"与"行盒高度"（越出 = 压到后面的文字 / 压到相邻行）；
 * - 图上绝不会被源码的排版宽度（可以比图宽 2~4 倍）直接锁死 —— 那是 §14.6 的坑。
 *
 * 数字取自 1440×2560 / density 640 的真机实测（设计文档 §14.6）。
 */
class AtomBoxTest {

    @Test
    fun `含 CJK 的行内公式不被行盒压小_只允许有限溢出`() {
        // 实测那条：`$W_{\text{发}}=W_{\text{收}}=W$` 图 477x98，行盒 82px（普通 ASCII 行）。
        // 只读态的视觉高度就是图高，所以编辑态必须能画到接近原尺寸 —— 压到 84% 就是 §14.5 报的"偏小"。
        val scale = AtomBox.drawScale(477, 98, availableWidthPx = 943f, availableHeightPx = 82f)
        assertTrue("不该被行盒压小，实际 scale=$scale", scale > 0.95f)
        // 溢出仍然有界：画出来的高度不超过行盒的 MAX_HEIGHT_OVERFLOW 倍
        val drawn = 98f * scale
        assertTrue(
            "画高不得超过行盒的 ${AtomBox.MAX_HEIGHT_OVERFLOW} 倍，实际 $drawn",
            drawn <= 82f * AtomBox.MAX_HEIGHT_OVERFLOW + 0.01f
        )
    }

    @Test
    fun `真正超高的公式仍按行盒缩`() {
        // `\sum` 那类：图 573x211，行盒 104px → 211 > 104×1.25，必须缩
        val (w, h) = AtomBox.drawSize(573, 211, availableWidthPx = 1173f, availableHeightPx = 104f)
        assertTrue("必须缩到行盒的溢出余量之内，实际 $h", h <= 104f * AtomBox.MAX_HEIGHT_OVERFLOW + 0.01f)
        assertEquals("长宽比必须保持", 573f / 211f, w / h, 0.01f)
    }

    @Test
    fun `图比可用空间小的时候不放大`() {
        // 与只读态同尺寸：只读路径的占位高 = 图高，图有多大画多大
        assertEquals(
            1f,
            AtomBox.drawScale(162, 73, availableWidthPx = 1200f, availableHeightPx = 104f),
            0.001f
        )
        // 哪怕空间大得离谱也不放大
        assertEquals(
            1f,
            AtomBox.drawScale(162, 73, availableWidthPx = 99999f, availableHeightPx = 99999f),
            0.001f
        )
    }

    @Test
    fun `图比行盒高时按行盒缩_长宽比不变`() {
        // 实测：`\sum ...` 图 573x211，行盒 104px，行宽 1232px
        val (w, h) = AtomBox.drawSize(573, 211, availableWidthPx = 1232f, availableHeightPx = 104f)
        assertTrue("高度必须落进行盒的溢出余量，实际 $h", h <= 104f * AtomBox.MAX_HEIGHT_OVERFLOW + 0.01f)
        assertTrue("宽度也必须落进行宽，实际 $w", w <= 1232f)
        assertEquals("长宽比必须保持（573/211 ≈ 2.716）", 573f / 211f, w / h, 0.01f)
    }

    @Test
    fun `超长公式被行宽限制_不会压到后面的字`() {
        // 行宽只剩 300px 时，878px 宽的图必须缩到 300px 以内
        val (w, _) = AtomBox.drawSize(878, 211, availableWidthPx = 300f, availableHeightPx = 104f)
        assertTrue("画宽不得超过行宽，实际 $w", w <= 300f + 0.01f)
    }

    @Test
    fun `源码比图宽得多时也不受影响_上限只看行宽与行盒`() {
        // 实测那条被字距压成 208px 的 68 字符源码（图 573x211）：
        // 旧实现拿 208px 当上限，画出来 229px（溢出，压住后面的字）。
        // 现在宽度上限是**行宽**，与源码占宽无关。
        val (w, h) = AtomBox.drawSize(573, 211, availableWidthPx = 1173f, availableHeightPx = 104f)
        assertTrue("画宽不得超过行宽，实际 $w", w <= 1173f)
        assertTrue("画高不得超过行盒的溢出余量，实际 $h", h <= 104f * AtomBox.MAX_HEIGHT_OVERFLOW + 0.01f)
    }

    @Test
    fun `绘制缩放下限保证图不会小到看不见`() {
        val scale = AtomBox.drawScale(573, 211, availableWidthPx = 10f, availableHeightPx = 10f)
        assertEquals(AtomBox.MIN_DRAW_SCALE, scale, 0.001f)
    }

    @Test
    fun `尺寸未就绪时不缩放`() {
        assertEquals(1f, AtomBox.drawScale(0, 0, 100f, 100f), 0.001f)
        assertEquals(1f, AtomBox.drawScale(100, 50, 0f, 0f), 0.001f)
        assertEquals(1f, AtomBox.drawScale(100, 50, -5f, 100f), 0.001f)
    }

    @Test
    fun `两个上限里更紧的那个说了算`() {
        // 宽限制更紧：可用 200x1000 装 800x100 的图 → 0.25
        assertEquals(
            0.25f,
            AtomBox.drawScale(800, 100, availableWidthPx = 200f, availableHeightPx = 1000f),
            0.001f
        )
        // 高限制更紧：可用 2000x50 装 800x200 的图 → (50×1.25)/200 = 0.3125
        assertEquals(
            0.3125f,
            AtomBox.drawScale(800, 200, availableWidthPx = 2000f, availableHeightPx = 50f),
            0.001f
        )
    }
}
