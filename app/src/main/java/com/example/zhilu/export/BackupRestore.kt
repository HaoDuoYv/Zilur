package com.example.zhilu.export

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.Note

/**
 * 备份里的笔记 → **可以直接 insert 的形态**。
 *
 * ## 为什么需要这一步
 *
 * 备份里的 id 是导出时那台设备的主键，直接拿去插入会撞主键；而块与块之间还有两类引用：
 * `cardId`（块属于哪张卡片）与 `parentBranchId`（分支子块属于哪个分支）。
 * 三者必须**成套重写**，漏掉任何一类的表现都是"数据进得去、结构全散了"：
 * 子块变成顶层块、卡片归错、甚至整篇被 `hydrate` 合并成一张卡。
 *
 * 做法与 `NoteViewModel` / AI 工具保持一致：**统一的负临时 id**，由
 * `NoteRepositoryImpl.replaceCards/replaceBlocks` 的 id 映射换成真实主键。
 *
 * 纯函数，不依赖 Android，可直接单测。
 *
 * @param mediaIdMap 旧媒体 id → 新媒体 id（导入时媒体行会被重建）
 */
fun remapForInsert(note: Note, mediaIdMap: Map<Long, Long> = emptyMap()): Note {
    val allBlocks = note.contentBlocks
    // 旧 id → 临时 id。重复 id（手工改过的备份可能出现）取最后一个，属于病态输入，
    // 不为此增加复杂度；正常备份里 id 唯一。
    val tempIdByOldId = allBlocks
        .mapIndexed { index, block -> block.id to -(index + 1L) }
        .toMap()

    fun prepare(block: Block, cardTempId: Long): Block = block.copy(
        id = tempIdByOldId[block.id] ?: 0L,
        noteId = 0L,
        cardId = cardTempId,
        // 分支层级：父块的旧 id 换成同一个临时 id 空间里的值，仓储层才好重映射
        parentBranchId = block.parentBranchId?.let { tempIdByOldId[it] },
        content = remapImageContent(block, mediaIdMap)
    )

    if (note.cards.isEmpty()) {
        return note.copy(id = 0L, blocks = allBlocks.map { prepare(it, cardTempId = 0L) })
    }

    val cards = note.cards.mapIndexed { index, card ->
        val tempId = -(index + 1L)
        card.copy(id = tempId, blocks = card.blocks.map { prepare(it, tempId) })
    }
    // 有卡片时 blocks 必须留空：Note.contentBlocks 是"blocks 非空就只用 blocks"，
    // 两边都填会让卡片结构被静默忽略。
    return note.copy(id = 0L, blocks = emptyList(), cards = cards)
}

/** 图片块：媒体 id 换成导入后新建的那一行，uri 保持备份里的值（渲染优先按 mediaId 解析）。 */
private fun remapImageContent(block: Block, mediaIdMap: Map<Long, Long>): String {
    if (block.type != BlockType.IMAGE || mediaIdMap.isEmpty()) return block.content
    val oldMediaId = ImageBlockContent.mediaId(block.content) ?: return block.content
    val newMediaId = mediaIdMap[oldMediaId] ?: return block.content
    return ImageBlockContent.fromMedia(newMediaId, ImageBlockContent.displayUri(block.content))
}
