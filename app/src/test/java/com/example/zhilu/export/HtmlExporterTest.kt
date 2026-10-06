package com.example.zhilu.export

import android.content.Context
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.Note
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HtmlExporterTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val mediaFileManager = MediaFileManager(context)
    private val exporter = HtmlExporter(context, mediaFileManager)

    @Test
    fun exportNote_containsTitleAndText() = runTest {
        val note = Note(
            title = "红黑树",
            blocks = listOf(Block(type = BlockType.TEXT, content = "节点定义"))
        )
        val result = exporter.exportNote(note)
        assertTrue(result.isSuccess)
        val html = result.getOrThrow()
        assertTrue(html.contains("红黑树"))
        assertTrue(html.contains("节点定义"))
        assertTrue(html.contains("<!DOCTYPE html>"))
    }

    @Test
    fun exportNote_codeBlockIsWrapped() = runTest {
        val note = Note(
            blocks = listOf(Block(type = BlockType.CODE, content = "int x = 1;", language = "cpp"))
        )
        val result = exporter.exportNote(note)
        assertTrue(result.isSuccess)
        val html = result.getOrThrow()
        assertTrue(html.contains("<code class=\"language-cpp\">"))
        assertTrue(html.contains("int x = 1;"))
    }

    /**
     * 代码块在导出里也要是**深色终端**，而不是浅灰底。
     *
     * 与 app 内 `PalettePaint.codeSurface`（动森 `#2B2118` + `#E8D5BC`）保持一致 ——
     * 导出件与 app 里看到的应当是同一个东西，否则用户会以为导出坏了。
     */
    @Test
    fun exportNote_codeBlockUsesTerminalPalette() = runTest {
        val html = exporter.exportNote(
            Note(blocks = listOf(Block(type = BlockType.CODE, content = "let x = 1", language = "kotlin")))
        ).getOrThrow()

        assertTrue("代码块底应是深色终端色（#2B2118）", html.contains("#2B2118"))
        assertTrue("代码块字应是暖米色（#E8D5BC）", html.contains("#E8D5BC"))
        assertTrue("pre code 要不透出自己的浅底", html.contains("pre code { background: none;"))
    }

    @Test
    fun exportNote_dividerBecomesHr() = runTest {
        val html = exporter.exportNote(
            Note(blocks = listOf(Block(type = BlockType.DIVIDER, content = "")))
        ).getOrThrow()

        assertTrue("分割线块要导出成 <hr>", html.contains("<hr>"))
    }
}
