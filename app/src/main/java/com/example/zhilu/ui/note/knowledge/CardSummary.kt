package com.example.zhilu.ui.note.knowledge

import com.example.zhilu.domain.markup.InlineMarkup
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.KnowledgeCard

/**
 * 卡片的摘要行（设计文档 §5.1、§5.3）。
 *
 * 取卡内第一段**有内容的 TEXT** 的首句，截断一行。
 *
 * 两个必须遵守的细节：
 * 1. **先剥离行内语法再截断** —— 否则摘要里会冒出 `{{k:` 这类标记字符，
 *    而且它们还会占掉本就只有几十个字的预算（§3.10 第 3 条）；
 * 2. 只取顶层块 —— 分支子块是父块的从属内容，不该抢摘要。
 *
 * 纯函数，不依赖 Compose，方便单测。
 */
fun cardSummary(card: KnowledgeCard, maxChars: Int = 42): String? {
    val source = card.blocks
        .filter { it.parentBranchId == null }
        .firstOrNull { it.type == BlockType.TEXT && it.content.isNotBlank() }
        ?.content
        ?: card.blocks
            .filter { it.parentBranchId == null }
            .firstOrNull { it.type == BlockType.BRANCH && it.content.isNotBlank() }
            ?.content
        ?: return null

    val stripped = InlineMarkup.stripMarkup(source)
        .replace('\n', ' ')
        .trim()
    if (stripped.isEmpty()) return null

    val firstSentence = stripped.substringBefore('。').ifBlank { stripped }
    return if (firstSentence.length > maxChars) firstSentence.take(maxChars) + "…" else firstSentence
}
