package com.example.zhilu.domain.search

/**
 * 中文检索的 token 化规则（纯函数，**唯一来源**：索引写入与查询构造共用一份）。
 *
 * ## 为什么需要它
 *
 * FTS 的 `unicode61` 分词器把连续 CJK 字符视为**单个** alphanumeric 串：
 * 「二次型的惯性定理与规范形」整串是一个 token，搜「惯性定理」0 命中 ——
 * 直接拿 unicode61 索引中文原文会让搜索比 `LIKE` 还差（本地实测见
 * `docs/ai-retrieval-and-prompt-cache.md` §D2）。
 *
 * ## 方案：应用层 bigram
 *
 * 写入索引前与查询前各做一次**同构**变换，FTS 只存/查空格分隔的 token 流：
 * 「惯性定理」→「惯性 性定 定理」。查询侧同构变换后按 AND 匹配，语义等于
 * **精确子串**（跨标点/字段边界自然断开，不会产生伪 bigram）。
 *
 * ## 已知边界（有意为之，非缺陷）
 *
 * - **单字查询**：只命中原文中独立成段的单字（「火」「焰」「山」分开出现时），
 *   不命中「火焰山」词内的单字 —— bigram 索引里没有单字 token。
 *   单字搜索本就是噪声重灾区，2 字以上才是目标场景。
 * - **ASCII 词**：整词保留（lowercase），`c++` 中的 `+` 是分隔符 → token 只有 `c`；
 *   查询侧对 ASCII token 追加 `*` 前缀通配，覆盖「只输一半」（`kub` → `kub*`）。
 * - 生僻字（CJK 扩展 B 及以后）被当作分隔符，不参与分词。
 */
object NoteSearchTokens {

    /**
     * 词段识别：连续汉字（含扩展 A 区生僻字）或连续 ASCII 字母/数字/下划线。
     * 其余一切字符（标点、空白、`+` `-` `.` 等）都是**分隔符**。
     */
    private val SEGMENT = Regex("[\\u3400-\\u9FFF]+|[A-Za-z0-9_]+")

    private fun isCjk(code: Int): Boolean = code in 0x3400..0x9FFF

    /**
     * 把一段文本切成检索 token 序列（已去重、保序）。
     *
     * - 汉字段：长度 > 1 时按 2 字滑窗取全部 bigram；单字段保留自身；
     * - ASCII 段：整词 lowercase。
     */
    fun tokenize(text: String): List<String> {
        val result = LinkedHashSet<String>()
        for (match in SEGMENT.findAll(text)) {
            val segment = match.value
            if (isCjk(segment[0].code)) {
                if (segment.length == 1) {
                    result.add(segment)
                } else {
                    for (i in 0 until segment.length - 1) {
                        result.add(segment.substring(i, i + 2))
                    }
                }
            } else {
                result.add(segment.lowercase())
            }
        }
        return result.toList()
    }

    /**
     * 组装写入 FTS 的索引正文。
     *
     * [segments] 是笔记的多个字段（标题 / 各内容块 / 各标签名），**必须分别 tokenize**
     * 再用换行拼接 —— 整体拼接后再分词会在字段边界处产生伪 bigram
     * （如标题尾「惯性」与正文头「定理」拼出「性定」）。
     */
    fun buildIndexBody(segments: List<String>): String =
        segments.filter { it.isNotBlank() }
            .joinToString("\n") { tokenize(it).joinToString(" ") }

    /**
     * 构造 FTS MATCH 查询串；无有效 token 时返回 null（调用方应直接给空结果）。
     *
     * **多词之间只用空格连接** —— FTS3/4 里空格就是「隐式 AND」（多词自然收紧）。
     * 切勿改成显式 ` AND `：`AND` 只在编译了 `SQLITE_ENABLE_FTS3_PARENTHESIS` 的
     * "增强查询语法"下才是运算符，**Android 自带的 SQLite 不认**（实测 Android 14 /
     * SQLite 3.32.2 上 `MATCH '矩阵 AND 矩阵'` 返回 0，`AND` 被当成一个普通搜索词）。
     * 隐式 AND 在两种语法下行为一致，是唯一可移植的写法。
     *
     * ASCII token 追加 `*` 前缀通配。token 字符集由 [SEGMENT] 保证安全
     * （不含查询语法里的引号、星号、括号、连字符）。
     */
    fun buildMatchQuery(keyword: String): String? {
        val tokens = tokenize(keyword)
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { token ->
            if (isCjk(token[0].code)) token else "$token*"
        }
    }
}
