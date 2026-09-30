package com.example.zhilu.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightMatchesTest {
    @Test
    fun emptyQueryReturnsPlainText() {
        val result = highlightMatches("线性代数笔记", "")
        assertEquals("线性代数笔记", result.text)
    }

    @Test
    fun hashPrefixIsStripped() {
        val result = highlightMatches("线性代数笔记", "#线性")
        assertTrue(result.text.contains("线性代数笔记"))
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun multipleMatchesAreHighlighted() {
        val result = highlightMatches("矩阵和矩阵分解", "矩阵")
        assertEquals("矩阵和矩阵分解", result.text)
        assertEquals(2, result.spanStyles.size)
    }

    @Test
    fun matchingIsCaseInsensitive() {
        val result = highlightMatches("Hello World", "hello")
        assertEquals(1, result.spanStyles.size)
    }
}
