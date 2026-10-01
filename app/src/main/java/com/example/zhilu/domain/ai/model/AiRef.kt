package com.example.zhilu.domain.ai.model

/**
 * 引用目标的类型：整篇笔记 / 笔记内某张知识卡片 / 卡片内某个块。
 */
enum class AiRefKind { NOTE, CARD, BLOCK }

/**
 * AI 引用：把笔记、知识卡片或块作为「上下文目标」附加到一次 AI 提问上。
 *
 * - 展示层用它渲染引用 chip（类型 + 标题）；
 * - 任务层用它锁定目标（生成期间该目标显示生成态并禁点）；
 * - 上下文层用它读取真实内容注入 LLM。
 *
 * 引用不落库，仅作为当次请求的上下文。
 */
data class AiRef(
    val kind: AiRefKind,
    val noteId: Long,
    val cardId: Long? = null,
    val blockId: Long? = null,
    val title: String = ""
) {
    /** 判断该引用是否命中某个「笔记 / 卡片 / 块」坐标。 */
    fun matches(noteId: Long, cardId: Long? = null, blockId: Long? = null): Boolean {
        if (this.noteId != noteId) return false
        return when (kind) {
            AiRefKind.NOTE -> true
            AiRefKind.CARD -> this.cardId == cardId
            AiRefKind.BLOCK -> this.blockId == blockId
        }
    }

    /** 判断该引用是否「占用」某张卡片（NOTE 命中整篇、CARD 命中该卡、BLOCK 命中卡内某块）。 */
    fun locksCard(noteId: Long, cardId: Long, cardBlockIds: Collection<Long>): Boolean {
        if (this.noteId != noteId) return false
        return when (kind) {
            AiRefKind.NOTE -> true
            AiRefKind.CARD -> this.cardId == cardId
            AiRefKind.BLOCK -> {
                val bid = this.blockId
                bid != null && bid in cardBlockIds
            }
        }
    }

    /** 判断该引用是否「占用」某个块（NOTE 命中整篇、CARD 命中块所在卡、BLOCK 命中该块）。 */
    fun locksBlock(noteId: Long, cardId: Long, blockId: Long): Boolean {
        if (this.noteId != noteId) return false
        return when (kind) {
            AiRefKind.NOTE -> true
            AiRefKind.CARD -> this.cardId == cardId
            AiRefKind.BLOCK -> this.blockId == blockId
        }
    }
}
