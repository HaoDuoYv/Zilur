package com.example.zhilu.ui.component

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightMatchesTest {

    // 强调色由调用方注入，测试里给一个固定的哨兵值即可——
    // 本用例只验「哪些片段被加粗」，不关心具体色值。
    private val highlight = Color(0xFF123456)

    @Test
    fun emptyQueryReturnsPlainText() {
        val result = highlightMatches("线性代数笔记", "", highlight)
        assertEquals("线性代数笔记", result.text)
    }

    @Test
    fun hashPrefixIsStripped() {
        val result = highlightMatches("线性代数笔记", "#线性", highlight)
        assertTrue(result.text.contains("线性代数笔记"))
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun multipleMatchesAreHighlighted() {
        val result = highlightMatches("矩阵和矩阵分解", "矩阵", highlight)
        assertEquals("矩阵和矩阵分解", result.text)
        assertEquals(2, result.spanStyles.size)
    }

    @Test
    fun matchingIsCaseInsensitive() {
        val result = highlightMatches("Hello World", "hello", highlight)
        assertEquals(1, result.spanStyles.size)
    }

    @Test
    fun callerSuppliesTheHighlightColor() {
        val accent = Color(0xFF7C5C31)
        val result = highlightMatches("笔记", "笔", accent)
        assertEquals(accent, result.spanStyles.single().item.color)
    }
}
