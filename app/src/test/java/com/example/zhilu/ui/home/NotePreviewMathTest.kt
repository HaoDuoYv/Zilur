package com.example.zhilu.ui.home

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 首页摘要的纯文本化。
 *
 * 真机反馈："首页把行内公式的 $ 原样显示出来了"。首页那行摘要是 `Text` 而不是 `RichText`，
 * 渲染不了公式图片，所以 `$…$` 只能去掉定界符、留下源码。
 */
class NotePreviewMathTest {

    @Test
    fun `行内公式去掉定界符保留源码`() {
        assertEquals("n = 编号比特数", plainPreviewText("\$n\$ = 编号比特数"))
        assertEquals("序号空间 = 2^n", plainPreviewText("序号空间 = \$2^n\$"))
    }

    @Test
    fun `块级公式去掉双定界符`() {
        assertEquals("x^2 + y^2", plainPreviewText("\$\$x^2 + y^2\$\$"))
    }

    @Test
    fun `行内代码去掉反引号`() {
        assertEquals("调用 foo() 即可", plainPreviewText("调用 `foo()` 即可"))
    }

    @Test
    fun `语义标记与加粗仍然被剥离`() {
        assertEquals("要点在这里", plainPreviewText("{{k:要点}}**在这里**"))
    }

    @Test
    fun `多行内容逐行处理`() {
        val raw = "· \$n\$ = 编号比特数\n· 序号空间 = \$2^n\$"
        assertEquals("· n = 编号比特数\n· 序号空间 = 2^n", plainPreviewText(raw))
    }

    @Test
    fun `没有公式时原样返回`() {
        assertEquals("普通文本", plainPreviewText("普通文本"))
    }

    @Test
    fun `孤立美元符号不被吃掉`() {
        // 只成对才算定界符：金额、孤立符号不能被吞
        assertEquals("价格 5$ 一件", plainPreviewText("价格 5\$ 一件"))
    }

    @Test
    fun `notePreviewText 走同一条纯文本化`() {
        val note = Note(
            id = 1,
            title = "网络",
            blocks = listOf(
                Block(id = 1, type = BlockType.TEXT, content = "· \$n\$ = 编号比特数", sortOrder = 0)
            )
        )
        val preview = notePreviewText(note)
        assertEquals("· n = 编号比特数", preview)
        assertFalse(preview!!.contains("$"))
    }

    @Test
    fun `公式块的摘要也去掉定界符`() {
        val note = Note(
            id = 1,
            title = "公式",
            blocks = listOf(Block(id = 1, type = BlockType.LATEX, content = "\$\$E = mc^2\$\$", sortOrder = 0))
        )
        assertEquals("E = mc^2", notePreviewText(note))
    }
}
