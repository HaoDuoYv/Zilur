package com.example.zhilu.ui.note.find

import com.example.zhilu.domain.markup.InlineMarkup
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.KnowledgeCard

/**
 * 一处命中。
 *
 * [cardIndex] 是跳转目标：块在卡片内部是**非懒加载**的（设计文档 §6.6），
 * 所以"跳到这一块"实际就是"跳到它所在的卡片"，同卡内的多处命中共享同一个滚动目标。
 *
 * [start]/[end] 是**可见文本**（剥离行内语法后）里的下标，供渲染层做高亮。
 */
data class FindMatch(
    val cardIndex: Int,
    val blockId: Long,
    val start: Int,
    val end: Int
)

/**
 * 页内查找的纯逻辑（设计文档 §6.4）。
 *
 * 硬要求：**匹配前必须先剥离行内语法**（§3.10 第 4 条）——
 * 否则搜「正」会把 `{{k:` 里的字符也算成命中，而且命中位置会落在被剥离的标记字符上，
 * 高亮就会画错地方。
 *
 * 只搜会显示文字的块（TEXT / BRANCH / CODE / LATEX / LINK / TODO）；图片与分割线跳过。
 */
fun findMatches(cards: List<KnowledgeCard>, query: String): List<FindMatch> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()

    val matches = mutableListOf<FindMatch>()
    cards.forEachIndexed { cardIndex, card ->
        card.blocks.forEach { block ->
            if (block.type == BlockType.IMAGE || block.type == BlockType.DIVIDER) return@forEach
            // TODO 块可能把内容放在块正文里（AI 写入路径），所以也纳入搜索
            val visible = InlineMarkup.stripMarkup(block.content)
            if (visible.isEmpty()) return@forEach
            val haystack = visible.lowercase()
            val target = needle.lowercase()
            var from = 0
            while (true) {
                val at = haystack.indexOf(target, from)
                if (at < 0) break
                matches += FindMatch(
                    cardIndex = cardIndex,
                    blockId = block.id,
                    start = at,
                    end = at + target.length
                )
                from = at + target.length
            }
        }
    }
    return matches
}
