package com.example.zhilu.export

import android.content.Context
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.Note
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DtkExporterTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val mediaFileManager = MediaFileManager(context)
    private val exporter = DtkExporter(context, mediaFileManager)

    @Test
    fun exportNote_createsDtkFile() = runTest {
        val note = Note(
            title = "test-note",
            blocks = listOf(Block(type = BlockType.TEXT, content = "hello", sortOrder = 0))
        )
        val result = exporter.exportNote(note)
        assertTrue(result.isSuccess)
        val file = result.getOrThrow()
        assertTrue(file.exists())
        assertEquals("dtk", file.extension)
    }
}
