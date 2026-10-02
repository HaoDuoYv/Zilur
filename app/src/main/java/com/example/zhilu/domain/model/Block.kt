package com.example.zhilu.domain.model

enum class BlockType(val value: Int) {
    TEXT(1),
    IMAGE(2),
    LINK(3),
    DIVIDER(4),
    LATEX(5),
    CODE(6),
    TODO(7),
    BRANCH(8);

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
