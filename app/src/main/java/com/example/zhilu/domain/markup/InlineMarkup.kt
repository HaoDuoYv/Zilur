package com.example.zhilu.domain.markup

import com.example.zhilu.domain.model.EmphasisTone

/**
 * 解析结果：剥离语法后的**可见文本** + 对应的标记表。
 *
 * [spans] 的坐标都在 [visibleText] 上，而不是在带语法字符的原文上。
 */
data class ParsedInline(
    val visibleText: String,
    val spans: List<InlineSpan>
) {
    companion object {
        val EMPTY = ParsedInline("", emptyList())
    }
}

/**
 * 内联标记的**唯一**词法器：语法 ↔ 可见文本。
 *
 * ## 语法（设计文档 §3.6.1）
 * ```
 * 标记 := "\\" ("{{" | "}}")        -- 转义，输出字面量本身
 *       | "{{" 角色 [笔触] ":" 内容 "}}"
 * 角色 := "k" | "i" | "w" | "t"
 * 笔触 := ""（荧光笔，默认） | "-c"（纯变色） | "-u"（下划线）
 * 内容 := 非空、不含 "}}"、不含 "{"/"}"、不含换行
 * ```
 * **角色前缀必填**：没有裸写形式，也不存在"最近一次使用的角色"这类会话状态 ——
 * 同一段文本在任何时候渲染结果都一样。
 *
 * ## 容错（硬要求）
 * 角色非法、缺 `:`、内容为空、含 `}}`、跨行、未闭合、嵌套 —— 一律**原样显示**，
 * 不吞字符、不报错。AI 与用户都可能写错，写错不能造成正文缺失。
 *
 * ## 与既有行内格式的关系
 * `**粗体**` / `` `行内代码` `` / `$公式$` 是**既有能力，本文件不处理**（也不隐藏），
 * 它们由 `ui/component/RichText.kt` 的词法器负责，优先级高于本语法
 * （代码 > 公式 > 加粗 > 语义色）。本文件只负责语义标记这一层。
 */
object InlineMarkup {

    /** `{{k:文字}}` / `{{k-u:文字}}` —— 内容非空、不含花括号与换行。 */
    internal val MARKER_REGEX = Regex("\\{\\{([kiwt])(-[cu])?:([^{}\\n]+)\\}\\}")

    /** `**加粗**` —— 内容非空、不含花括号、不含换行、不含 `**` 自身。 */
    private const val BOLD_DELIMITER = "**"

    // ---- 解析 ----

    /** 带语法字符的存储形态 → 可见文本 + 标记表。 */
    fun parseSpans(content: String): ParsedInline {
        if (content.isEmpty()) return ParsedInline.EMPTY

        val visible = StringBuilder(content.length)
        val spans = mutableListOf<InlineSpan>()
        var index = 0
        while (index < content.length) {
            if (content.isMarkupEscapeAt(index, '{')) {
                visible.append("{{")
                index += 3
                continue
            }
            if (content.isMarkupEscapeAt(index, '}')) {
                visible.append("}}")
                index += 3
                continue
            }
            if (content.startsWith(BOLD_DELIMITER, index)) {
                val close = content.indexOf(BOLD_DELIMITER, index + BOLD_DELIMITER.length)
                val body = if (close < 0) "" else content.substring(index + BOLD_DELIMITER.length, close)
                // 内容必须非空、单行、不含花括号与 `**`（否则边界有歧义）—— 不满足就原样显示
                if (body.isNotEmpty() && isCleanBoldBody(body)) {
                    val start = visible.length
                    visible.append(body)
                    spans += InlineSpan(EmphasisTone.KEY, InlineBrush.BOLD, start, visible.length)
                    index = close + BOLD_DELIMITER.length
                    continue
                }
            }
            if (content[index] == '{' && content.getOrNull(index + 1) == '{') {
                val match = MARKER_REGEX.find(content, index)
                if (match != null && match.range.first == index) {
                    val tone = EmphasisTone.fromCode(match.groupValues[1][0])
                    if (tone != null) {
                        val brush = InlineBrush.fromSuffix(match.groupValues[2].ifEmpty { null })
                        val start = visible.length
                        visible.append(match.groupValues[3])
                        spans += InlineSpan(tone, brush, start, visible.length)
                        index = match.range.last + 1
                        continue
                    }
                }
            }
            visible.append(content[index])
            index++
        }
        return ParsedInline(visible.toString(), spans)
    }

    /**
     * 加粗内容是否"干净"到可以无歧义地读回来。
     *
     * 花括号会与 `{{}}` 语法打架，`**` 会与自身定界符打架，换行则与"内联标记不跨行"冲突。
     */
    internal fun isCleanBoldBody(body: String): Boolean =
        body.none { it == '{' || it == '}' || it == '\n' } && !body.contains(BOLD_DELIMITER)

    /**
     * 只取可见文本（纯文本出口共用）。
     *
     * 快速通道必须同时检查 `{`、`}` 与 `*`：转义序列 `\}}` 里没有 `{`，
     * 只看 `{` 会让 `文字\}}` 原样返回、把转义反斜杠漏给用户；`*` 则可能开启一段加粗。
     */
    fun stripMarkup(content: String): String =
        if (!content.contains('{') && !content.contains('}') && !content.contains('*')) {
            content
        } else {
            parseSpans(content).visibleText
        }

    internal fun String.isMarkupEscapeAt(index: Int, brace: Char): Boolean =
        this[index] == '\\' &&
            getOrNull(index + 1) == brace &&
            getOrNull(index + 2) == brace

    // ---- 物化 ----

    /**
     * 可见文本 + 标记表 → 存储形态。
     *
     * 两条硬约束：
     * - 零长标记（「标记中」）不物化；
     * - **标记内容不能含花括号**（语法规定 `内容 := 不含 "{" / "}"`），
     *   所以当标记覆盖的文字里出现花括号时，必须在花括号处把标记**拆开**，
     *   把花括号转义后放在标记之外 —— 不能在标记内部转义，那样解析器读不回来。
     *
     * 于是 `materialize(parseSpans(合法文本)) == 合法文本`。
     */
    fun materialize(visibleText: String, spans: List<InlineSpan>): String {
        val usable = InlineSpanAdjuster.normalize(spans, visibleText).filter { it.coversText() }
        if (usable.isEmpty()) return escapeBraces(visibleText)

        val builder = StringBuilder(visibleText.length + usable.size * 8)
        var cursor = 0
        for (span in usable) {
            if (span.start > cursor) {
                builder.append(escapeBraces(visibleText.substring(cursor, span.start)))
            }
            appendSpan(builder, visibleText, span)
            cursor = span.end
        }
        if (cursor < visibleText.length) {
            builder.append(escapeBraces(visibleText.substring(cursor)))
        }
        return builder.toString()
    }

    /** 写一条标记；遇花括号就断开标记，把花括号转义后放到标记之外。 */
    private fun appendSpan(builder: StringBuilder, visibleText: String, span: InlineSpan) {
        // 加粗走 `**…**` 那一套，而且**不需要**为花括号拆段 —— `{{}}` 的"内容不含花括号"
        // 是那套语法的约束，加粗没有。但加粗自己的内容若带花括号或 `**`，
        // 读回来会有歧义，此时宁可丢掉格式、保住文字（与本文件一贯的容错口径一致）。
        if (span.brush == InlineBrush.BOLD) {
            val body = visibleText.substring(span.start, span.end)
            if (isCleanBoldBody(body)) {
                builder.append(BOLD_DELIMITER).append(body).append(BOLD_DELIMITER)
            } else {
                builder.append(escapeBraces(body))
            }
            return
        }

        val prefix = buildString {
            append("{{").append(span.tone.code).append(span.brush.suffix).append(':')
        }
        var runStart = span.start
        var index = span.start
        while (index < span.end) {
            val char = visibleText[index]
            if (char != '{' && char != '}') {
                index++
                continue
            }
            if (index > runStart) {
                builder.append(prefix).append(visibleText, runStart, index).append("}}")
            }
            var runEnd = index
            while (runEnd < span.end && (visibleText[runEnd] == '{' || visibleText[runEnd] == '}')) {
                runEnd++
            }
            builder.append(escapeBraces(visibleText.substring(index, runEnd)))
            index = runEnd
            runStart = runEnd
        }
        if (runStart < span.end) {
            builder.append(prefix).append(visibleText, runStart, span.end).append("}}")
        }
    }

    /** 把正文里的字面量 `{{` / `}}` 转义，避免被当成标记边界。 */
    fun escapeBraces(text: String): String {
        if (!text.contains('{') && !text.contains('}')) return text
        val builder = StringBuilder(text.length + 8)
        var index = 0
        while (index < text.length) {
            val char = text[index]
            val next = text.getOrNull(index + 1)
            if (char == '{' && next == '{') {
                builder.append("\\{{")
                index += 2
            } else if (char == '}' && next == '}') {
                builder.append("\\}}")
                index += 2
            } else {
                builder.append(char)
                index++
            }
        }
        return builder.toString()
    }
}
