package com.example.zhilu.domain.model

import com.example.zhilu.domain.usecase.clipboard.BlockClipboardData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 块树结构操作。
 *
 * 这一块每次出错都是**静默的数据损坏**（顺序错乱、子块挂到别的分支、粘贴后全挂同一个父块），
 * 所以每条规则都单独锁一条断言。
 */
class BlockOrderingTest {

    private fun block(
        id: Long,
        content: String = "块$id",
        type: BlockType = BlockType.TEXT,
        parentBranchId: Long? = null,
        sortOrder: Int = 0
    ) = Block(
        id = id,
        type = type,
        content = content,
        sortOrder = sortOrder,
        parentBranchId = parentBranchId
    )

    // ── 重排序号 ────────────────────────────────────────────────────────

    @Test
    fun `renumber 重排成连续序号`() {
        val blocks = listOf(block(1, sortOrder = 7), block(2, sortOrder = 30), block(3, sortOrder = 3))
        assertEquals(listOf(0, 1, 2), BlockOrdering.renumber(blocks).map { it.sortOrder })
        // 内容与 id 不动
        assertEquals(listOf(1L, 2L, 3L), BlockOrdering.renumber(blocks).map { it.id })
    }

    // ── 上下移动的邻居 ──────────────────────────────────────────────────

    /** 三个顶层块 + 一个挂在 1 号分支下的子块。 */
    private val blocks = listOf(
        block(1),
        block(2),
        block(11, parentBranchId = 1),
        block(3)
    )

    @Test
    fun `上移找到前一个顶层块`() {
        assertEquals(1L, BlockOrdering.neighborAbove(blocks, 2L))
        assertEquals(2L, BlockOrdering.neighborAbove(blocks, 3L))
    }

    @Test
    fun `第一个块不能再上移`() {
        assertNull(BlockOrdering.neighborAbove(blocks, 1L))
        assertNull(BlockOrdering.arrowUpSwap(blocks, 1L))
    }

    @Test
    fun `下移找到后一个顶层块`() {
        assertEquals(2L, BlockOrdering.neighborBelow(blocks, 1L))
        assertEquals(3L, BlockOrdering.neighborBelow(blocks, 2L))
    }

    @Test
    fun `最后一个块不能再下移`() {
        assertNull(BlockOrdering.neighborBelow(blocks, 3L))
        assertNull(BlockOrdering.arrowDownSwap(blocks, 3L))
    }

    @Test
    fun `不存在的块不产生移动`() {
        assertNull(BlockOrdering.neighborAbove(blocks, 999L))
        assertNull(BlockOrdering.neighborBelow(blocks, 999L))
    }

    @Test
    fun `分支子块不参与卡片级上下移动`() {
        assertNull(BlockOrdering.neighborAbove(blocks, 11L))
        assertNull(BlockOrdering.neighborBelow(blocks, 11L))
        assertEquals(1L, BlockOrdering.neighborAbove(blocks, 2L))
    }

    @Test
    fun `上移的参数是邻居换到自己后面`() {
        assertEquals(2L to 3L, BlockOrdering.arrowUpSwap(blocks, 3L))
    }

    @Test
    fun `下移的参数是自己换到邻居后面`() {
        assertEquals(1L to 2L, BlockOrdering.arrowDownSwap(blocks, 1L))
    }

    @Test
    fun `单块卡片两个方向都不能动`() {
        val single = listOf(block(1))
        assertNull(BlockOrdering.arrowUpSwap(single, 1L))
        assertNull(BlockOrdering.arrowDownSwap(single, 1L))
    }

    // ── move：整表重排 ──────────────────────────────────────────────────

    @Test
    fun `往上挪落到目标之前`() {
        val moved = BlockOrdering.move(listOf(block(1), block(2), block(3)), fromId = 3, toId = 2)!!
        assertEquals(listOf(1L, 3L, 2L), moved.map { it.id })
    }

    @Test
    fun `往下挪落到目标之后`() {
        val moved = BlockOrdering.move(listOf(block(1), block(2), block(3)), fromId = 1, toId = 2)!!
        assertEquals(listOf(2L, 1L, 3L), moved.map { it.id })
    }

    @Test
    fun `分支块连子块一起搬`() {
        val list = listOf(
            block(1),
            block(2, type = BlockType.BRANCH),
            block(21, parentBranchId = 2),
            block(22, parentBranchId = 2),
            block(3)
        )
        val moved = BlockOrdering.move(list, fromId = 2, toId = 3)!!
        // 分支与它的两个子块必须**相邻且保持父子关系**
        assertEquals(listOf(1L, 3L, 2L, 21L, 22L), moved.map { it.id })
        assertEquals(listOf(null, null, null, 2L, 2L), moved.map { it.parentBranchId })
    }

    @Test
    fun `分支往上搬时也带着子块`() {
        val list = listOf(
            block(1),
            block(2),
            block(3, type = BlockType.BRANCH),
            block(31, parentBranchId = 3),
            block(4)
        )
        val moved = BlockOrdering.move(list, fromId = 3, toId = 1)!!
        assertEquals(listOf(3L, 31L, 1L, 2L, 4L), moved.map { it.id })
    }

    @Test
    fun `子块不能被单独移动`() {
        val list = listOf(block(1), block(2, type = BlockType.BRANCH), block(21, parentBranchId = 2))
        assertNull(BlockOrdering.move(list, fromId = 21, toId = 1))
        // 反向也不行：目标块是子块时同样拒绝
        assertNull(BlockOrdering.move(list, fromId = 1, toId = 21))
    }

    @Test
    fun `移动到自己是空操作`() {
        assertNull(BlockOrdering.move(listOf(block(1), block(2)), fromId = 1, toId = 1))
    }

    @Test
    fun `块不存在时不改表`() {
        assertNull(BlockOrdering.move(listOf(block(1)), fromId = 1, toId = 999))
        assertNull(BlockOrdering.move(listOf(block(1)), fromId = 999, toId = 1))
    }

    // ── 粘贴：摊平 + 发新 id ────────────────────────────────────────────

    @Test
    fun `flatten 把嵌套模板摊平并记下父下标`() {
        val template = BlockClipboardData(
            block = block(1, type = BlockType.BRANCH),
            children = listOf(
                BlockClipboardData(block(2)),
                BlockClipboardData(block(3), children = listOf(BlockClipboardData(block(4))))
            )
        )

        val flat = BlockOrdering.flatten(template)

        assertEquals(listOf(1L, 2L, 3L, 4L), flat.map { it.first.id })
        // 父下标：分支是顶层(null)，2/3 挂在 0 号下，4 挂在 2 号下
        assertEquals(listOf(null, 0, 0, 2), flat.map { it.second })
    }

    @Test
    fun `revive 给每个块发不同的新 id_并重映射父子关系`() {
        val template = BlockClipboardData(
            block = block(1, type = BlockType.BRANCH),
            children = listOf(BlockClipboardData(block(2)), BlockClipboardData(block(3)))
        )
        var counter = -1L
        val revived = BlockOrdering.revive(BlockOrdering.flatten(template)) { counter-- }

        // id 全换新，且互不相同
        assertEquals(listOf(-1L, -2L, -3L), revived.map { it.id })
        assertEquals(3, revived.map { it.id }.toSet().size)
        // 两个子块指向的都是**新**的父 id，而不是模板里的旧 id 1
        assertEquals(listOf(null, -1L, -1L), revived.map { it.parentBranchId })
        assertTrue("子块绝不能留默认的 0", revived.drop(1).none { it.parentBranchId == 0L })
    }

    @Test
    fun `revive 把 sortOrder 归零_由调用方重排`() {
        val flat = BlockOrdering.flatten(BlockClipboardData(block(7, sortOrder = 42)))
        val revived = BlockOrdering.revive(flat) { -1L }
        assertEquals(listOf(0), revived.map { it.sortOrder })
    }

    @Test
    fun `空模板不发 id`() {
        var called = 0
        val revived = BlockOrdering.revive(emptyList()) { called++; -1L }
        assertTrue(revived.isEmpty())
        assertEquals("空模板不该消耗 id", 0, called)
    }
}
