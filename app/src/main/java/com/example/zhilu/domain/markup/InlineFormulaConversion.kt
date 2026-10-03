package com.example.zhilu.domain.markup

/**
 * 行内公式 ↔ 公式块（`LATEX` 块）的互转。
 *
 * 为什么需要它：行内公式适合"句子里带一个符号"，但**长公式不该待在句子中间** ——
 * 它会折行、被行高封顶压小，而且读起来也不再是句子的一部分（设计文档 §12.3 的遗留问题）。
 * 提升成公式块后它居中、独立成行、可以单独排序与编号；反过来也要能降级回句子里。
 *
 * 纯函数、不依赖 Compose，方便单测。落库格式不变：行内是 `$…$`，块里是**裸 LaTeX 源码**
 * （与 AI 工具、导出的约定一致）。
 */
object InlineFormulaConversion {

    /**
     * 提升：把一条行内公式从正文里摘出来。
     *
     * @return `(摘掉公式之后的正文, 公式源码)`；不是公式、或公式内容为空时返回 null
     */
    fun promote(text: String, node: InlineNode): Pair<String, String>? {
        if (node.kind != InlineKind.MATH) return null
        val start = node.start.coerceIn(0, text.length)
        val end = node.end.coerceIn(0, text.length)
        if (end <= start) return null
        val source = node.contentOf(text)
        if (source.isBlank()) return null

        val before = text.substring(0, start)
        val after = text.substring(end)
        // 摘掉公式时顺手把多余的那个空格收掉，否则会留下「调用  结束」这种双空格，
        // 或者公式在行首时留下一个前导空格（块的开头带空格，观感与缩进都不对）。
        val kept = when {
            before.isEmpty() -> after.removePrefix(" ")
            after.isEmpty() -> before.removeSuffix(" ")
            before.endsWith(" ") && after.startsWith(" ") -> before.dropLast(1) + after
            else -> before + after
        }
        return kept to source
    }

    /**
     * 降级：公式源码 → 行内形态（`$…$`）。
     *
     * 公式块允许多行（`\\` 换行、`\begin{array}` 等），行内形态**必须单行**，所以把连续空白
     * 压成一个空格。压完为空则返回 null（空公式没有降级的意义）。
     */
    fun demoteToInline(latexSource: String): String? {
        val oneLine = latexSource.replace(WHITESPACE, " ").trim()
        if (oneLine.isEmpty()) return null
        return "\$$oneLine\$"
    }

    private val WHITESPACE = Regex("\\s+")
}
