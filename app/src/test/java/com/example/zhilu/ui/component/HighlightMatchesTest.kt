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

    @Test
    fun multipleKeywordsAreEachHighlighted() {
        // 检索侧把空格分隔的多词按 AND 处理，高亮侧必须同构 —— 否则两个词都命中了却一处都不亮
        val result = highlightMatches("实对称矩阵一一对应", "矩阵 对称", highlight)
        assertEquals("实对称矩阵一一对应", result.text)
        assertEquals(2, result.spanStyles.size)
    }

    @Test
    fun overlappingKeywordHitsAreNotDoubleCounted() {
        val result = highlightMatches("对称矩阵", "对称 对称矩阵", highlight)
        assertEquals("对称矩阵", result.text)
        assertEquals(1, result.spanStyles.size)
    }

    @Test
    fun blankSeparatedKeywordsAreIgnored() {
        val result = highlightMatches("线性代数", "  线性   ", highlight)
        assertEquals("线性代数", result.text)
        assertEquals(1, result.spanStyles.size)
    }
}
