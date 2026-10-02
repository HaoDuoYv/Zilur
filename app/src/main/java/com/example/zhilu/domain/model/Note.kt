package com.example.zhilu.domain.model

data class Note(
    val id: Long = 0,
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val deletedAt: Long? = null,
    val blocks: List<Block> = emptyList(),
    val cards: List<KnowledgeCard> = emptyList(),
    val tags: List<Tag> = emptyList()
) {
    /**
     * 全部内容块，按卡片顺序摊平。
     *
     * 这是取「笔记正文」的**唯一入口**。原因：从数据库读出来的笔记（`NoteRepositoryImpl.hydrate`）
     * 只会填 [blocks] 与 [cards] 中的**一处**——
     * - 有知识卡片的笔记：块挂在 `cards[i].blocks` 下，[blocks] 是**空的**；
     * - 没有卡片的笔记：块才填在 [blocks]。
     *
     * 因此直接读 [blocks] 会把「有卡片的笔记」看成空笔记。历史上这个不一致造成过两类事故：
     * 一是 `updateNote` 只按 [blocks] 落库，改标题/改标签/收藏时把正文整段删掉；
     * 二是 AI 读取笔记时拿到空正文，据此回复「该笔记正文为空」。
     *
     * 写侧同样走这里，见 `NoteRepositoryImpl.replaceBlocks`。
     */
    val contentBlocks: List<Block>
        get() = if (blocks.isNotEmpty()) blocks else cards.flatMap { it.blocks }
}
