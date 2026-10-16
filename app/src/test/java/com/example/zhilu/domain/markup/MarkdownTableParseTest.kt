package com.example.zhilu.domain.markup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 表格解析是「表格块」与「AI 消息里的 Markdown 表格」共用的唯一一份。
 *
 * 判据错了的表现是两类静默故障：把普通正文误判成表格（渲染出一张莫名其妙的网格），
 * 或者把真表格漏判（内容原样以管道文本显示，看起来像功能没做）。
 */
class MarkdownTableParseTest {

    @Test
    fun parsesStandardPipeTable() {
        val table = MarkdownTable.parseWhole(
            "| 项目 | 说明 |\n| --- | --- |\n| 惯性 | 合同不变 |\n| 规范形 | 对角化 |"
        )!!

        assertEquals(listOf("项目", "说明"), table.header)
        assertEquals(listOf(listOf("惯性", "合同不变"), listOf("规范形", "对角化")), table.rows)
        assertEquals(2, table.columnCount)
    }

    @Test
    fun missingSeparatorRowIsNotATable() {
        // 少了 | --- | 分隔行 → 不成表，调用方落回纯文本（内容不丢，只是不成网格）
        assertNull(MarkdownTable.parseWhole("| 项目 | 说明 |\n| 惯性 | 合同不变 |"))
    }

    @Test
    fun proseWithLonePipeIsNotATable() {
        assertNull(MarkdownTable.parseWhole("a | b 只是正文里用了竖线"))
    }

    @Test
    fun parsesAlignedSeparatorRow() {
        // :--- / ---: 这类对齐写法也要认，否则从别处粘来的表格会解析失败
        val table = MarkdownTable.parseWhole("| a | b |\n| :--- | ---: |\n| 1 | 2 |")!!
        assertEquals(listOf("a", "b"), table.header)
        assertEquals(1, table.rows.size)
    }

    @Test
    fun escapedPipeStaysInOneCell() {
        val table = MarkdownTable.parseWhole("| 符号 | 含义 |\n| --- | --- |\n| a \\| b | 或 |")!!
        assertEquals(listOf("a | b", "或"), table.rows.single())
    }

    @Test
    fun trailingProseAfterTableMakesParseWholeReturnNull() {
        // parseWhole 要求整段就是一张表；块里混了表外文字说明用它（会落回纯文本）
        assertNull(
            MarkdownTable.parseWhole("| a | b |\n| --- | --- |\n| 1 | 2 |\n\n补充一句说明。")
        )
    }

    @Test
    fun parseAtFindsTableInTheMiddleOfDocument() {
        // 文档级解析（AI 消息）用 parseAt：表格可以夹在段落之间，靠 lineCount 跳过整张表
        val lines = listOf("开头一段", "| a | b |", "| --- | --- |", "| 1 | 2 |", "结尾一段")

        val table = MarkdownTable.parseAt(lines, 1)!!

        assertEquals(listOf("a", "b"), table.header)
        assertEquals(3, table.lineCount)
        assertNull("从普通行开始不该识别出表格", MarkdownTable.parseAt(lines, 0))
    }

    @Test
    fun headerWithoutBodyStillParses() {
        val table = MarkdownTable.parseWhole("| 列 1 | 列 2 |\n| --- | --- |")!!
        assertTrue(table.rows.isEmpty())
        assertEquals(2, table.columnCount)
    }

    @Test
    fun raggedRowsStillReportMaxColumnCount() {
        // 某行列多了也要照常渲染出来，不能丢内容
        val table = MarkdownTable.parseWhole("| a | b |\n| --- | --- |\n| 1 |\n| 2 | 3 | 4 |")!!
        assertEquals(3, table.columnCount)
        assertFalse(table.rows.isEmpty())
    }

    @Test
    fun templateIsParseable() {
        // 编辑态那个「插入表格模板」按钮给的骨架必须自己能解析，
        // 否则用户一点就得到一段"还差一行表头分隔"的提示
        assertTrue(MarkdownTable.parseWhole(MarkdownTable.TEMPLATE) != null)
    }
}
