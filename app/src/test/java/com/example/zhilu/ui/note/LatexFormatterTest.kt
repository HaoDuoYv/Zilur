package com.example.zhilu.ui.note

import org.junit.Assert.assertEquals
import org.junit.Test

class LatexFormatterTest {
    @Test
    fun formatsCommonLatexForReading() {
        val rendered = LatexFormatter.format("\\alpha^2 + \\frac{a}{b} + \\sqrt{x}")

        assertEquals("α² + a⁄b + √(x)", rendered)
    }
}
