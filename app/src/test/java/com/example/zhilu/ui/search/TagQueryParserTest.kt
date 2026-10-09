package com.example.zhilu.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `#标签名` 的解析规则。
 *
 * 这套规则同时喂给**输入补全**与**提交归一**，所以边界要钉死：
 * 认哪些、不认哪些，两边必须一致。
 */
class TagQueryParserTest {

    // ---- 认得出来的 ----

    @Test
    fun `whole query is a tag reference`() {
        val token = TagQueryParser.trailingToken("#计算机")

        assertEquals("计算机", token?.name)
        assertEquals(0, token?.start)
        assertEquals(4, token?.end)
        assertEquals("", TagQueryParser.strip("#计算机", token!!))
    }

    @Test
    fun `trailing word after a keyword is a tag reference`() {
        val token = TagQueryParser.trailingToken("论文 #计算")

        assertEquals("计算", token?.name)
        assertEquals("论文", TagQueryParser.strip("论文 #计算", token!!))
    }

    @Test
    fun `lone hash yields an empty token so the full list can be offered`() {
        val token = TagQueryParser.trailingToken("#")

        assertEquals("", token?.name)
        assertEquals("", TagQueryParser.strip("#", token!!))
    }

    @Test
    fun `only the last word counts`() {
        // 前面的 # 已经落成 chip 了，用户接着打的这个才是正在输入的
        val token = TagQueryParser.trailingToken("#旧 #新")

        assertEquals("新", token?.name)
        assertEquals("#旧", TagQueryParser.strip("#旧 #新", token!!))
    }

    @Test
    fun `leading and trailing blanks are tolerated`() {
        // 输入法常在词后补空格；`论文 #计算 ` 与 `论文 #计算` 必须是同一个 token，
        // 否则候选会在用户按下空格的一瞬间消失
        val token = TagQueryParser.trailingToken("  论文 #计算  ")

        assertEquals("计算", token?.name)
        assertEquals("论文", TagQueryParser.strip("  论文 #计算  ", token!!))
    }

    // ---- 不该认的 ----

    @Test
    fun `hash inside a word is not a tag reference`() {
        // C# 是正文里真实存在的词，不该被当语法吞掉
        assertNull(TagQueryParser.trailingToken("C#"))
        assertNull(TagQueryParser.trailingToken("学 C# 语法"))
    }

    @Test
    fun `hash glued to the front of a word is not a token`() {
        // 词首必须是 # 才算；foo#bar 是"搜带井号的正文"
        assertNull(TagQueryParser.trailingToken("foo#bar"))
    }

    @Test
    fun `plain keyword has no token`() {
        assertNull(TagQueryParser.trailingToken("线性代数"))
        assertNull(TagQueryParser.trailingToken(""))
        assertNull(TagQueryParser.trailingToken("   "))
    }

    @Test
    fun `strip keeps the rest of the query intact`() {
        val token = TagQueryParser.trailingToken("a b #标签")!!
        // 只摘 token，前面的关键词原样保留（这是"补全"与"归一"能共用一条路径的原因）
        assertEquals("a b", TagQueryParser.strip("a b #标签", token))
    }
}
