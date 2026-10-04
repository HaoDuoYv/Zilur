package com.example.zhilu.domain.model

import com.example.zhilu.domain.usecase.clipboard.BlockClipboardData

/**
 * 块树的**结构性操作**：顺序与父子关系的纯计算。
 *
 * 抽出来的理由不是"文件太长"，而是这一块**每次出错都是静默的数据损坏**：
 * 块的顺序和 `parentBranchId` 是 `_blocks` 扁平表里唯一的层级真相，算错一位不会报错，
 * 只会让子块挂到别的分支下、或者让正文顺序错乱 —— 而这两种都有真机事故记录
 * （见 AGENTS.md 里"分支子块绝不能都留默认的 0"那条）。纯函数 + 单测是这里唯一划算的防线。
 */
object BlockOrdering {

    /** 卡片级的"可移动块"：只有顶层块参与卡片内的上下移动。 */
    fun topLevel(blocks: List<Block>): List<Block> = blocks.filter { it.parentBranchId == null }

    /**
     * 把块表重排成**连续序号**（0..n-1）。
     *
     * 库里 `sortOrder` 只要求相对有序，但连续序号让"插入到第 i 位"这类操作的预期变得可断言。
     */
    fun renumber(blocks: List<Block>): List<Block> =
        blocks.mapIndexed { index, block -> block.copy(sortOrder = index) }

    /**
     * 上移时该与哪个块交换，返回 null 表示"已经在最上面，不动"。
     */
    fun neighborAbove(blocks: List<Block>, blockId: Long): Long? {
        val ordered = topLevel(blocks)
        val index = ordered.indexOfFirst { it.id == blockId }
        return if (index <= 0) null else ordered[index - 1].id
    }

    /**
     * 下移时该与哪个块交换，返回 null 表示"已经在最下面，不动"。
     */
    fun neighborBelow(blocks: List<Block>, blockId: Long): Long? {
        val ordered = topLevel(blocks)
        val index = ordered.indexOfFirst { it.id == blockId }
        return if (index < 0 || index >= ordered.lastIndex) null else ordered[index + 1].id
    }

    /**
     * 上移要调用的 `move` 参数（from, to）。
     *
     * 上移是"把上面的邻居挪到我后面"，下移是"把我挪到下面的邻居后面" ——
     * 两个方向传参的**主客关系是反的**。写反了不会报错，只会表现为"点了箭头不动"
     * （[move] 先算 `fromIndex < toIndex` 再决定插到目标前还是后），所以参数在这里算好并单测锁住。
     */
    fun arrowUpSwap(blocks: List<Block>, blockId: Long): Pair<Long, Long>? =
        neighborAbove(blocks, blockId)?.let { it to blockId }

    /** 下移要调用的 `move` 参数（from, to）。 */
    fun arrowDownSwap(blocks: List<Block>, blockId: Long): Pair<Long, Long>? =
        neighborBelow(blocks, blockId)?.let { blockId to it }

    /**
     * 把 [fromId] 挪到 [toId] 的前面或后面，返回**新的块表**（不改原表）。
     *
     * 方向由两者的原始前后关系决定：往下挪就落到目标之后，往上挪就落到目标之前。
     *
     * **分支块连子块一起搬**：`BRANCH` 块与它的子块（`parentBranchId == 分支 id`）是一个整体，
     * 只搬父块会把子块留在原地 —— 那等于把树拆了。
     *
     * 返回 null 表示这次移动不成立（块不存在、任一不是顶层块、或就是自己），
     * 调用方应当**原样保留**原表而不是当成"移动成功但没变化"。
     */
    fun move(blocks: List<Block>, fromId: Long, toId: Long): List<Block>? {
        if (fromId == toId) return null
        val fromBlock = blocks.find { it.id == fromId } ?: return null
        val toBlock = blocks.find { it.id == toId } ?: return null
        // 子块的位置由它所属分支决定，不参与卡片级移动
        if (fromBlock.parentBranchId != null || toBlock.parentBranchId != null) return null

        val fromIndex = blocks.indexOfFirst { it.id == fromId }
        val toIndex = blocks.indexOfFirst { it.id == toId }
        if (fromIndex < 0 || toIndex < 0) return null

        val moving = if (fromBlock.type == BlockType.BRANCH) {
            listOf(fromBlock) + blocks.filter { it.parentBranchId == fromId }
        } else {
            listOf(fromBlock)
        }

        val rest = blocks.filterNot { candidate -> moving.any { it.id == candidate.id } }
        // 目标块可能在"被搬走的整组"里（理论不可达，防御一下）——那就保持原样
        val remainingTargetIndex = rest.indexOfFirst { it.id == toId }
        if (remainingTargetIndex < 0) return null
        // 往下挪：落到目标之后；往上挪：落到目标之前
        val insertIndex = if (fromIndex < toIndex) remainingTargetIndex + 1 else remainingTargetIndex
        return rest.toMutableList().apply {
            addAll(insertIndex.coerceIn(0, size), moving)
        }
    }

    /**
     * 把剪贴板里的**嵌套模板**摊平成 "块 + 它在结果里的父下标"（父为 null = 顶层）。
     *
     * 先摊平再统一分配新 id 的原因：父块的新 id 必须先算出来，子块才能指向它；
     * 边遍历边发 id 也能做，但那样"父子关系"就散在递归里、无法单独断言。
     */
    fun flatten(template: BlockClipboardData): List<Pair<Block, Int?>> {
        val result = mutableListOf<Pair<Block, Int?>>()
        fun traverse(data: BlockClipboardData, parentIndex: Int?) {
            val currentIndex = result.size
            result.add(data.block to parentIndex)
            data.children.forEach { traverse(it, currentIndex) }
        }
        traverse(template, null)
        return result
    }

    /**
     * 给摊平后的模板发**新 id**，并把父下标映射成新的父 id。
     *
     * @param nextId 取一个全新的临时 id。**必须由调用方提供**：它是实例级递减计数器，
     *   而粘贴是一次性预分配（`List(n) { nextId() }`），把这个语义收进纯函数会把它变成隐藏状态。
     */
    fun revive(flat: List<Pair<Block, Int?>>, nextId: () -> Long): List<Block> {
        if (flat.isEmpty()) return emptyList()
        val newIds = List(flat.size) { nextId() }
        return flat.mapIndexed { index, (source, parentIndex) ->
            source.copyWithFreshId(newIds[index]).copy(
                parentBranchId = parentIndex?.let { newIds[it] },
                sortOrder = 0
            )
        }
    }
}
