package com.example.zhilu.domain.markup

/**
 * GFM 管道表格的解析（纯函数，无 Android 依赖）。
 *
 * ## 为什么表格用「Markdown 文本」而不是结构化 JSON 存
 *
 * 表格块的内容就是一段管道表格文本：
 *
 * ```
 * | 项目 | 说明 |
 * | --- | --- |
 * | A | 第一行 |
 * ```
 *
 * 这样选是四个约束同时满足的结果：
 * 1. **可降级**：老版本 App 不认识 `TABLE` 类型时按 `fromValue` 回退成文本块，
 *    管道文本照常可读，只是不成网格 —— 比 JSON 结构体降级成乱码好得多；
 * 2. **导出天然**：Markdown 原样输出，HTML 转 `<table>`，检索索引也能正常分词；
 * 3. **AI 友好**：模型本来就会写 GFM 表格，不需要额外教一套结构；
 * 4. **编辑简单**：与 `CODE` / `LATEX` 一样是"文本 + 渲染"，不需要单元格聚焦那套状态机。
 *
 * ## 解析口径
 *
 * 与 AI 消息里的 Markdown 渲染**共用这一份**（原先那套是 `AiMessageContent` 里的
 * private 函数）。判据刻意宽容：一行里有两个以上 `|` 就算表格行，分隔行是
 * `---` / `:---:` 这类；`\|` 会被还原成字面的 `|`。
 */
object MarkdownTable {

    /**
     * 一张解析好的表。
     *
     * @param lineCount 源文本里这张表占了几行（表头 + 分隔行 + 数据行）——
     *   文档级解析要靠它跳过整张表，而不是逐行重猜。
     */
    data class Table(
        val header: List<String>,
        val rows: List<List<String>>,
        val lineCount: Int
    ) {
        /** 列数取表头与各数据行的最大值：某行列少了就补空，列多了也不丢。 */
        val columnCount: Int get() = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
    }

    private val SEPARATOR_CELL = Regex("^:?-{2,}:?$")

    /** 行内有 ≥2 个 `|` 才算表格行（`a | b` 这种正文不会被误判）。 */
    fun isRow(line: String): Boolean = line.count { it == '|' } >= 2

    /** 分隔行：`| --- | --- |` 或 `| :---: |`。 */
    fun isSeparator(line: String): Boolean {
        val cells = splitCells(line)
        return cells.isNotEmpty() && cells.all { SEPARATOR_CELL.matches(it) }
    }

    /** 按未转义的 `|` 切分单元格，并还原模型输出的 `\|` 转义。 */
    fun splitCells(line: String): List<String> {
        // 占位符躲开 split，切完再还原 —— 直接 split('|') 会把转义竖线也切开
        val token = "\u0000"
        return line.trim()
            .removePrefix("|")
            .removeSuffix("|")
            .replace("\\|", token)
            .split('|')
            .map { it.trim().replace(token, "|") }
    }

    /**
     * 从 [lines] 的 [start] 行开始尝试解析一张表；不成立返回 null（调用方按普通行处理）。
     *
     * 成立条件是**表头行 + 紧随其后的分隔行**同时存在 —— 只看表头会把
     * 「正文里恰好用了两个竖线」的普通行误判成表格。
     */
    fun parseAt(lines: List<String>, start: Int): Table? {
        if (start < 0 || start + 1 >= lines.size) return null
        val headerLine = lines[start]
        if (!isRow(headerLine) || isSeparator(headerLine)) return null
        if (!isSeparator(lines[start + 1])) return null

        val rows = mutableListOf<List<String>>()
        var index = start + 2
        while (index < lines.size && isRow(lines[index]) && !isSeparator(lines[index])) {
            rows.add(splitCells(lines[index]))
            index++
        }
        return Table(
            header = splitCells(headerLine),
            rows = rows,
            lineCount = index - start
        )
    }

    /**
     * 整段内容恰好就是一张表时返回它；夹杂其他文字、或根本不成表时返回 null。
     *
     * 表格块用这个（块的内容**就是**表本身，不该有表外文字）；
     * 文档级解析走 [parseAt]（表可以夹在段落之间）。
     */
    fun parseWhole(content: String): Table? {
        val lines = content.lines()
        val table = parseAt(lines, 0) ?: return null
        val rest = lines.drop(table.lineCount).filter { it.isNotBlank() }
        return if (rest.isEmpty()) table else null
    }

    /** 空表格块的起手模板：两列一行，用户/模型改起来比从零写管道语法容易。 */
    const val TEMPLATE = "| 列 1 | 列 2 |\n| --- | --- |\n|  |  |"
}
