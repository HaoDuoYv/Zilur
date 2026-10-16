package com.example.zhilu.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockTypeTest {
    @Test
    fun advancedBlockTypesUseStableValues() {
        assertEquals(BlockType.LATEX, BlockType.fromValue(5))
        assertEquals(BlockType.CODE, BlockType.fromValue(6))
    }

    @Test
    fun todoBlockTypeUsesStableValue() {
        assertEquals(BlockType.TODO, BlockType.fromValue(7))
        assertEquals(7, BlockType.TODO.value)
    }

    @Test
    fun branchBlockTypeUsesStableValue() {
        assertEquals(BlockType.BRANCH, BlockType.fromValue(8))
        assertEquals(8, BlockType.BRANCH.value)
    }

    @Test
    fun tableBlockTypeUsesStableValue() {
        // 追加而非插入：value 是持久化稳定值（Room 的 type 列、导出 JSON 的兜底解析都靠它）
        assertEquals(BlockType.TABLE, BlockType.fromValue(9))
        assertEquals(9, BlockType.TABLE.value)
    }

    @Test
    fun unknownValueFallsBackToText() {
        // 老版本 App 读到新类型时按文本显示 —— 表格块存的是管道文本，降级后仍可读
        assertEquals(BlockType.TEXT, BlockType.fromValue(999))
    }
}
