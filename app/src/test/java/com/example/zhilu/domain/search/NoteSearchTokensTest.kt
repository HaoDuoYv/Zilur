package com.example.zhilu.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住 bigram 分词的同构性（索引侧与查询侧必须一致）与已知边界。
 *
 * 这些断言直接对应搜索的真机行为：tokenize 错了，表现是「明明有却搜不到」
 * 或「搜什么都命中全库」两类静默故障。
 */
class NoteSearchTokensTest {

    @Test
    fun `汉字串切成全量 bigram`() {
        assertEquals(listOf("惯性", "性定", "定理"), NoteSearchTokens.tokenize("惯性定理"))
    }

    @Test
    fun `长句中的目标词产出的 bigram 与独立词一致`() {
        val body = NoteSearchTokens.tokenize("二次型的惯性定理与规范形")
        assertTrue(body.containsAll(listOf("惯性", "性定", "定理")))
    }

    @Test
    fun `标点边界不产生伪 bigram`() {
        val tokens = NoteSearchTokens.tokenize("惯性，定理")
        assertEquals(listOf("惯性", "定理"), tokens)
        assertFalse("「性定」是跨标点的伪 bigram，不该出现", tokens.contains("性定"))
    }

    @Test
    fun `单字段保留自身`() {
        assertEquals(listOf("火"), NoteSearchTokens.tokenize("火"))
    }

    @Test
    fun `中英数字混排各自成 token`() {
        assertEquals(listOf("180", "度"), NoteSearchTokens.tokenize("180度"))
        assertEquals(listOf("20", "分钟"), NoteSearchTokens.tokenize("20分钟"))
    }

    @Test
    fun `ASCII 词整词保留并小写化 标点是分隔符`() {
        assertEquals(listOf("kubernetes"), NoteSearchTokens.tokenize("Kubernetes"))
        assertEquals(listOf("c"), NoteSearchTokens.tokenize("C++"))
        assertEquals(listOf("node", "js"), NoteSearchTokens.tokenize("node.js"))
    }

    @Test
    fun `重复内容去重且保序`() {
        assertEquals(listOf("惯性", "性定", "定理"), NoteSearchTokens.tokenize("惯性定理 惯性"))
    }

    @Test
    fun `纯标点与空白产出空 token`() {
        assertEquals(emptyList<String>(), NoteSearchTokens.tokenize("！？。、  "))
        assertEquals(emptyList<String>(), NoteSearchTokens.tokenize(""))
    }

    @Test
    fun `索引正文分词后空格连接 字段之间不跨界`() {
        val body = NoteSearchTokens.buildIndexBody(listOf("惯性", "定理", "数学"))
        // 标题尾「惯性」与正文头「定理」不拼出「性定」
        assertFalse("字段边界不得产生伪 bigram", body.contains("性定"))
        assertTrue(body.contains("惯性"))
        assertTrue(body.contains("定理"))
        assertTrue(body.contains("数学")) // 单字段自身是完整 token
    }

    @Test
    fun `MATCH 串只用空格连接且 ASCII 加前缀通配`() {
        val query = NoteSearchTokens.buildMatchQuery("惯性定理 kub")
        assertEquals("惯性 性定 定理 kub*", query)
    }

    /**
     * 这条守卫对应一个真机上踩过的坑：Android 自带 SQLite（3.32.2）**不认** `AND` 运算符，
     * `MATCH '矩阵 AND 矩阵'` 返回 0（`AND` 被当成普通搜索词），而空格连接的隐式 AND 正常。
     * 一旦有人把分隔符改回 ` AND `，中文检索会整体失效（≥3 字的词都会产出 ≥2 个 bigram），
     * 而且本地桌面 SQLite 上是好的、测不出来 —— 只能钉在这里。
     */
    @Test
    fun `MATCH 串不得使用显式 AND 运算符`() {
        val query = NoteSearchTokens.buildMatchQuery("惯性定理")!!
        assertFalse("显式 AND 在 Android SQLite 上不是运算符，只能用空格隐式 AND", query.contains("AND"))
        assertEquals(3, query.split(" ").size)
    }

    @Test
    fun `MATCH 串不含查询语法特殊字符`() {
        val query = NoteSearchTokens.buildMatchQuery("""c++ "kw" a-b (x) OR""")!!
        for (special in listOf("\"", "(", ")", "-", "++")) {
            assertFalse("MATCH 串不得含 $special", query.contains(special))
        }
        // OR 是小写化后的普通词，不是操作符
        assertTrue(query.contains("or*"))
    }

    @Test
    fun `无有效 token 时 MATCH 串为 null`() {
        assertNull(NoteSearchTokens.buildMatchQuery("!?。。"))
        assertNull(NoteSearchTokens.buildMatchQuery("  "))
    }

    @Test
    fun `索引与查询同构 —— 实际搜索词都能在索引里找到对应 token`() {
        val body = NoteSearchTokens.buildIndexBody(
            listOf("二次型的惯性定理与规范形", "性能优化的实践总结")
        )
        for (query in listOf("惯性定理", "定理", "性能优化", "优化", "二次型")) {
            val tokens = NoteSearchTokens.tokenize(query)
            val allPresent = tokens.all { body.contains(it) }
            assertTrue("查询「$query」的 token 应全部存在于索引正文", allPresent)
        }
    }
}
