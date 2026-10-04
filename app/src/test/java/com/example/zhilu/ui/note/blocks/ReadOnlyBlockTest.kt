package com.example.zhilu.ui.note.blocks

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadOnlyBlockTest {
    @Test
    fun `copy menu label is Chinese`() {
        assertEquals("复制此块", readOnlyBlockCopyMenuLabel())
    }

    @Test
    fun `clipboard text matches block content exactly`() {
        val block = Block(
            type = BlockType.TEXT,
            content = " first line\nsecond line "
        )

        assertEquals(block.content, readOnlyBlockClipboardText(block).text)
    }

    /**
     * 只读态"复制此块"粘出去的是**给人看的文字**：行内语法必须剥掉。
     *
     * 与上一条并不矛盾 —— 上一条钉的是"没有语法时别动原文"，这一条钉的是"有语法时要剥"。
     * 两条合起来才是这个出口的完整契约。
     */
    @Test
    fun `clipboard text strips inline markup`() {
        val block = Block(
            type = BlockType.TEXT,
            content = "标准形中{{i:正}}平方项的个数是{{k:惯性指数}}"
        )

        val copied = readOnlyBlockClipboardText(block).text

        assertEquals("标准形中正平方项的个数是惯性指数", copied)
        assertFalse("不该把语法字符粘出去：$copied", copied.contains("{{"))
    }

    @Test
    fun `latex block copies bare source, not the delimiters`() {
        val block = Block(type = BlockType.LATEX, content = "W \\le 2^{n-1}")

        val copied = readOnlyBlockClipboardText(block).text

        assertTrue("公式块复制的是裸源码：$copied", copied.startsWith("W"))
        assertFalse("不该带 \$\$ 包裹：$copied", copied.contains("$"))
    }
}
