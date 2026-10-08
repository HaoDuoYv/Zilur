package com.example.zhilu.domain.ai

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ImageBlockContent

/**
 * 引用内容快照：把「被引用的笔记 / 卡片 / 块」格式化为一段可交给模型的文本。
 *
 * ## 为什么需要它
 *
 * 引用不能按 id 回查数据库（见 `AiRef` 类注释：编辑器 id 是内存坐标，未保存/新建内容
 * 查不到）。所以内容在**点引用的那一刻**就由这里格式化并冻存进 `AiRef.snapshot`，
 * 之后无论笔记怎么改、有没有保存，AI 拿到的都是用户当时看到的那份内容。
 *
 * ## 约定
 *
 * - 头部**不写内部 id**：id 对模型没有价值，未保存内容的负数 id 还会误导它；
 * - 单条快照上限 [MAX_CHARS]，超出截断并标注，避免一篇超长笔记把请求撑爆；
 * - 块渲染与导出理念一致：code 用围栏、公式保留 `$$` 源码、待办写 `- [ ]`。
 */
object AiRefSnapshot {

    /** 单条引用快照的字符上限。超出按原文截断并加注记（不改语义）。 */
    const val MAX_CHARS = 12_000

    /** 整篇笔记的快照（[blocks] 传摊平后的全部正文块）。 */
    fun forNote(title: String, blocks: List<Block>): String =
        truncate("笔记《${title.ifBlank { "未命名笔记" }}》\n${formatBlocks(blocks)}")

    /** 某张知识卡片的快照。 */
    fun forCard(noteTitle: String, cardTitle: String, blocks: List<Block>): String =
        truncate(
            "笔记《${noteTitle.ifBlank { "未命名笔记" }}》中的卡片" +
                "《${cardTitle.ifBlank { "未命名卡片" }}》\n${formatBlocks(blocks)}"
        )

    /** 某个块的快照。 */
    fun forBlock(noteTitle: String, block: Block): String =
        truncate("笔记《${noteTitle.ifBlank { "未命名笔记" }}》中的块：\n${formatBlock(block)}")

    fun formatBlocks(blocks: List<Block>): String =
        blocks.joinToString("\n\n") { formatBlock(it) }

    /**
     * 单个块的文本渲染。
     *
     * 与 `AiTaskManager` 旧版逐字一致（那版已并入此处）——改这里等于同时改所有引用入口，
     * 想改渲染先改 [com.example.zhilu.domain.ai.AiRefSnapshotTest] 钉住的期望值。
     */
    fun formatBlock(block: Block): String = when (block.type) {
        BlockType.CODE -> "```${block.language.ifBlank { "text" }}\n${block.content}\n```"
        BlockType.LATEX -> "\$\$${block.content}\$\$"
        BlockType.LINK -> block.content
        BlockType.TODO -> "- [ ] ${block.content}"
        BlockType.DIVIDER -> "---"
        BlockType.BRANCH -> "【分支】${block.content}"
        BlockType.IMAGE -> "【图片】${ImageBlockContent.displayUri(block.content)}"
        else -> block.content
    }

    private fun truncate(raw: String): String =
        if (raw.length <= MAX_CHARS) raw else raw.take(MAX_CHARS) + "\n…（已截断）"
}
