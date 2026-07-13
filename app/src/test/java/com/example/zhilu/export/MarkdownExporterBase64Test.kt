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
class MarkdownExporterBase64Test {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val mediaFileManager = MediaFileManager(context)

    @Test
    fun exportNoteWithBase64_containsCodeBlock() = runTest {
        val note = Note(
            title = "测试",
            blocks = listOf(Block(type = BlockType.CODE, content = "val x = 1", language = "kotlin"))
        )
        val md = MarkdownExporter.exportNoteWithBase64(
            note = note,
            mediaFileManager = mediaFileManager,
            context = context
        )
        assertTrue(md.contains("```kotlin"))
        assertTrue(md.contains("val x = 1"))
        assertTrue(md.contains("# 测试"))
    }
}
