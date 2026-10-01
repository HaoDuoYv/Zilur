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

    /**
     * 含图片块的笔记导出不应崩溃。
     * 注：这里不断言图片被写进导出包——本机 Robolectric 跑在 Windows 上，
     * `Uri.fromFile().toString()` 会把 `C:\...` 编码成 `file://C:%5C...`，
     * 反解出的 path 为空，导致 copyToCache 必然失败（真机 POSIX 路径无此问题）。
     * 「裸 URI 也要能解析到媒体」这条回归由 ImageBlockContentTest#resolveMedia* 覆盖。
     */
    @Test
    fun exportNote_withImageBlockDoesNotFail() = runTest {
        val note = Note(
            title = "with-image",
            blocks = listOf(
                Block(type = BlockType.TEXT, content = "hi", sortOrder = 0),
                // 裸 URI：模拟 AI create_note / update_note 写进来的内容
                Block(type = BlockType.IMAGE, content = "file:///tmp/bare.png", sortOrder = 1)
            )
        )
        val result = exporter.exportNote(note)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().exists())
    }
}
