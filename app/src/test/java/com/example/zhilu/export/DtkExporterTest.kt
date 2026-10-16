package com.example.zhilu.export

import android.content.Context
import com.example.zhilu.data.local.file.ArchiveManager
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Note
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
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
     * 「裸 URI 也要能解析到媒体」这条回归由 ImageBlockContentTest#resolveMedia* 覆盖，
     * 完整的图片往返由 ImageBlockContent 与本类的 mock 版本用例覆盖。
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

    /**
     * 导出侧的图片契约：[`block.content` 必须携带 mediaId、`media[]` 去重]。
     *
     * 这里 mock 掉 `copyToCache`（Windows 上真实路径走不通），只为把「块内容被规范化」
     * 与「同一张图只写一条」这两件事钉住 —— 它们正是导入端能按 mediaId join 的前提。
     */
    @Test
    fun exportNotes_normalizesImageContentAndDedupesMedia() = runTest {
        val manager = mockk<MediaFileManager>()
        every { manager.copyToCache(any(), any()) } answers {
            val dest = File(secondArg<File>(), "media_7.png")
            dest.parentFile?.mkdirs()
            dest.writeText("img-bytes")
            Result.success(dest)
        }
        val imageUri = "file:///data/user/0/com.example.zhilu/files/images/img_a.png"
        val media = Media(id = 7L, uri = imageUri, size = 9L)
        val note = Note(
            title = "带图笔记",
            blocks = listOf(
                // 裸 URI：AI 早期写入的形态，没有 mediaId 前缀，规范化后才能被 join 上
                Block(type = BlockType.IMAGE, content = imageUri, sortOrder = 0),
                // 同一张图被第二个块引用 —— 媒体列表里只该出现一条
                Block(type = BlockType.IMAGE, content = "7|$imageUri", sortOrder = 1)
            )
        )

        val file = DtkExporter(context, manager).exportNotes(listOf(note), listOf(media)).getOrThrow()

        val unzipDir = File(context.cacheDir, "dtk_export_assert").apply {
            deleteRecursively()
            mkdirs()
        }
        ArchiveManager.unzip(file, unzipDir).getOrThrow()
        val json = File(unzipDir, "note.json").readText()
        unzipDir.deleteRecursively()

        val parsed = JsonExporter.importBackup(json)
        assertEquals("同一张图只该写一条媒体记录", 1, parsed.media.size)
        // zip 内的条目名 = media/<copyToCache 的落盘名> = media/media_<id>.<ext>
        assertEquals("media/media_7.png", parsed.media.single().uri)
        val blocks = parsed.notes.single().blocks
        assertEquals(2, blocks.size)
        assertTrue(
            "裸 URI 块也必须被规范化成「mediaId|uri」，实际=${blocks[0].content}",
            blocks[0].content.startsWith("7|")
        )
        assertTrue(blocks[1].content.startsWith("7|"))
        verify(exactly = 1) { manager.copyToCache(any(), any()) }
    }

    @Test
    fun exportNotes_setsDtkVersion2() = runTest {
        val note = Note(title = "版本", blocks = listOf(Block(type = BlockType.TEXT, content = "x")))
        val file = exporter.exportNotes(listOf(note)).getOrThrow()

        val unzipDir = File(context.cacheDir, "dtk_export_version").apply {
            deleteRecursively()
            mkdirs()
        }
        ArchiveManager.unzip(file, unzipDir).getOrThrow()
        val json = File(unzipDir, "note.json").readText()
        unzipDir.deleteRecursively()

        assertTrue(
            "v2 起多篇包才能被老版本 App 干净拒绝（否则静默只导第一篇）",
            json.contains("\"dtkVersion\":2")
        )
    }
}
