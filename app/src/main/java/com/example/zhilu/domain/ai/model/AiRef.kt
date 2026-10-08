package com.example.zhilu.domain.ai.model

import kotlinx.serialization.Serializable

/**
 * 引用目标的类型：整篇笔记 / 笔记内某张知识卡片 / 卡片内某个块。
 */
enum class AiRefKind { NOTE, CARD, BLOCK }

/**
 * AI 引用：把笔记、知识卡片或块作为「上下文目标」附加到一次 AI 提问上。
 *
 * - 展示层用它渲染引用 chip（类型 + 标题），并随用户消息落库（`ai_messages.refsJson`）；
 * - 任务层用它锁定目标（生成期间该目标显示生成态并禁点）；
 * - 上下文层把 [snapshot] 注入本轮提问（见 `AiRefSnapshot`）。
 *
 * ## 为什么内容走快照（[snapshot]）而不是按 id 回查
 *
 * 编辑器里的卡片/块 id 是**内存坐标**：新建内容为负数，且 `NoteViewModel.saveInternal`
 * 刻意不回写主键（改了会击穿 LazyColumn 的 key、丢输入焦点）。拿这套 id 去数据库查必然
 * miss，引用内容会退化成「卡片/块不存在」。因此改为在**点引用的那一刻**把内容格式化为
 * 文本快照冻存（`AiRefSnapshot`），AI 读快照；快照为空时才回退按 id 查库（防御路径）。
 *
 * 快照跟随引用一起落库：历史消息回看时 chip 仍可展示，追问轮引用原文仍可见。
 */
@Serializable
data class AiRef(
    val kind: AiRefKind,
    val noteId: Long,
    val cardId: Long? = null,
    val blockId: Long? = null,
    val title: String = "",
    /** 引用时刻的内容快照（已格式化文本，可能被截断）。见类注释。 */
    val snapshot: String = ""
) {
    /**
     * 引用目标坐标键：`kind:noteId:cardId:blockId`。
     *
     * 去重用它而不是 `equals` —— 同一目标先后被引用会带不同快照，`distinct()` 按全字段
     * 比较会把它们当成两个引用。
     */
    fun targetKey(): String = "${kind.name}:$noteId:${cardId ?: "-"}:${blockId ?: "-"}"

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

/**
 * 按坐标键去重，**同一目标保留最后出现的那一个**（最新引用的快照最接近用户当前所见）。
 *
 * 三处合流点（笔记页交接、跨页吸附、输入栏追加）共用，避免各写一遍。
 */
fun List<AiRef>.distinctByTarget(): List<AiRef> {
    val lastIndex = HashMap<String, Int>()
    forEachIndexed { index, ref -> lastIndex[ref.targetKey()] = index }
    return filterIndexed { index, ref -> lastIndex[ref.targetKey()] == index }
}
