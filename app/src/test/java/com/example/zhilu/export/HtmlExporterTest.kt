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
}
