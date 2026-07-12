package com.example.zhilu.ui.note.latex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexSanitizerTest {

    @Test
    fun bareFormulaWrappedInDisplayMath() {
        assertEquals("\$\$E=mc^2\$\$", sanitizeLatex("E=mc^2"))
    }

    @Test
    fun inlineFormulaNotDoubleWrapped() {
        assertEquals("\$E=mc^2\$", sanitizeLatex("\$E=mc^2\$"))
    }

    @Test
    fun inlineParenFormulaNotWrapped() {
        assertEquals("\\(E=mc^2\\)", sanitizeLatex("\\(E=mc^2\\)"))
    }

    @Test
    fun emptyInputReturnsEmpty() {
        assertEquals("", sanitizeLatex(""))
    }

    @Test
    fun alignStarConvertedToArray() {
        val input = "\\begin{align*}x &= 1 \\\\ y &= 2\\end{align*}"
        val output = sanitizeLatex(input)
        assertTrue(output.contains("\\begin{array}{rl}"))
        assertTrue(output.contains("\\end{array}"))
    }

    @Test
    fun displayMathDelimitersRemoved() {
        assertEquals("\$\$E=mc^2\$\$", sanitizeLatex("\\[E=mc^2\\]"))
    }

    @Test
    fun beginEnvironmentNotWrapped() {
        val input = "\\begin{array}{rl}1 & 2\\\\3 & 4\\end{array}"
        assertEquals(input, sanitizeLatex(input))
    }
}
