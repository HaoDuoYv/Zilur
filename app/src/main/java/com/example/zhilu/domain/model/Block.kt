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
    val parentBranchId: Long? = null
)
