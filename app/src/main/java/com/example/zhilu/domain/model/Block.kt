package com.example.zhilu.domain.model

/**
 * 内容块类型。
 *
 * `value` 是**持久化稳定值**（Room 的 `note_blocks.type`、导出 JSON 的兜底解析都靠它），
 * 只能追加、不可改序或改值。
 *
 * [fromValue] 对未知值静默回退 [TEXT]：老版本 App 读到新类型时，内容会按普通文本显示
 * —— 这是刻意的降级策略（表格块存的是 Markdown 管道文本，降级后仍可读）。
 */
enum class BlockType(val value: Int) {
    TEXT(1),
    IMAGE(2),
    LINK(3),
    DIVIDER(4),
    LATEX(5),
    CODE(6),
    TODO(7),
    BRANCH(8),

    /**
     * 表格。内容是一段 GFM 管道表格文本（见 `domain/markup/MarkdownTable`）——
     * 不发明新编码，导出、降级、检索都能天然承接。
     */
    TABLE(9);

    companion object {
        fun fromValue(value: Int): BlockType = entries.firstOrNull { it.value == value } ?: TEXT
    }
}

data class Block(
    val id: Long = 0,
    val noteId: Long = 0,
    val cardId: Long? = null,
    val type: BlockType = BlockType.TEXT,
    val content: String = "",
    val language: String = "",
    val sortOrder: Int = 0,
    val parentBranchId: Long? = null,
    /**
     * 块级语义标记（L1）。`null` = 未标记。
     *
     * 行内标记（L2）不存在这里 —— 它活在 [content] 文本里（`{{k:文字}}`），
     * 因此导出/导入/剪贴板/AI 全都天然承载。
     */
    val emphasis: EmphasisTone? = null
) {
    fun copyWithFreshId(newId: Long): Block = copy(
        id = newId,
        noteId = 0,
        cardId = 0,
        sortOrder = 0,
        parentBranchId = null
    )
}
