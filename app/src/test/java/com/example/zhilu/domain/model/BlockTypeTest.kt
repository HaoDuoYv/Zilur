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
}
