package com.example.zhilu.ui.note.knowledge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分支块展开态的取数规则。
 *
 * 存在的理由：只读态曾被写死成"永远展开"，于是箭头照画、点了没反应 ——
 * **分支内容永远收不起来**（真机反馈的"只读下只有常态展开，无法收起"）。
 * 这一条把"展开态只由状态表决定、与编辑态无关"钉住。
 */
class BranchExpandedRuleTest {

    @Test
    fun `没有记录时默认展开`() {
        assertTrue(isBranchExpanded(emptyMap(), branchId = 7L))
    }

    @Test
    fun `状态表说收起就是收起`() {
        assertFalse(isBranchExpanded(mapOf(7L to false), branchId = 7L))
    }

    @Test
    fun `状态表说展开就是展开`() {
        assertTrue(isBranchExpanded(mapOf(7L to true), branchId = 7L))
    }

    @Test
    fun `只看本分支自己的状态`() {
        val states = mapOf(1L to false, 2L to true)

        assertFalse(isBranchExpanded(states, branchId = 1L))
        assertTrue(isBranchExpanded(states, branchId = 2L))
        // 没记录的分支不受别人影响
        assertTrue(isBranchExpanded(states, branchId = 3L))
    }
}
