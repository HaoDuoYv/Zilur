package com.example.zhilu.data.local.file

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchiveManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun zipAndUnzipRoundTrip() {
        val sourceDir = tempFolder.newFolder("source")
        File(sourceDir, "note.json").writeText("{\"title\":\"test\"}")
        val mediaDir = File(sourceDir, "media").apply { mkdirs() }
        File(mediaDir, "img.png").writeText("fake-image")

        val zipFile = tempFolder.newFile("output.dtk")
        val result = ArchiveManager.zip(sourceDir, zipFile)
        assertTrue("打包应成功", result.isSuccess)

        val unzipDir = tempFolder.newFolder("unzip")
        val unzipResult = ArchiveManager.unzip(zipFile, unzipDir)
        assertTrue("解压应成功", unzipResult.isSuccess)
        assertTrue("note.json 应存在", File(unzipDir, "note.json").exists())
        assertTrue("media/img.png 应存在", File(unzipDir, "media/img.png").exists())
        assertEquals("{\"title\":\"test\"}", File(unzipDir, "note.json").readText())
    }

    // ── 下面四条是解包的安全防线。`.dtk` 可以从任何地方来，这层不过就是数据目录失守 ──

    @Test
    fun unzip_rejectsPathTraversalEntry() {
        val zipFile = writeZip(
            "traversal.dtk",
            listOf("../escaped.txt" to "pwned")
        )
        val result = ArchiveManager.unzip(zipFile, tempFolder.newFolder("traversal"))

        assertTrue("含 ../ 的包必须整包拒收", result.isFailure)
        assertFalse(
            "越权文件不得落盘（解到上一级目录）",
            File(tempFolder.root, "escaped.txt").exists()
        )
    }

    @Test
    fun unzip_rejectsBackslashPathTraversalEntry() {
        // 恶意包常用反斜杠绕过分隔符判断，归一化必须先做
        val zipFile = writeZip(
            "backslash.dtk",
            listOf("..\\escaped.txt" to "pwned")
        )
        val result = ArchiveManager.unzip(zipFile, tempFolder.newFolder("backslash"))

        assertTrue("反斜杠形式的 ../ 同样必须拒收", result.isFailure)
        assertFalse(File(tempFolder.root, "escaped.txt").exists())
    }

    @Test
    fun unzip_rejectsTooManyEntries() {
        val zipFile = writeZip(
            "many.dtk",
            (0 until 3).map { "f$it.txt" to "x" }
        )
        val result = ArchiveManager.unzip(
            zipFile,
            tempFolder.newFolder("many"),
            ZipLimits(maxEntries = 2)
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun unzip_rejectsOversizedEntry() {
        val zipFile = writeZip("big.dtk", listOf("big.bin" to "x".repeat(100)))
        val result = ArchiveManager.unzip(
            zipFile,
            tempFolder.newFolder("big"),
            ZipLimits(maxEntryBytes = 10)
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun unzip_rejectsOversizedTotal() {
        // 单个条目都没超，但累加超了 —— 累计值没写对的话这条会漏
        val zipFile = writeZip(
            "total.dtk",
            listOf("a.bin" to "x".repeat(100), "b.bin" to "x".repeat(100))
        )
        val result = ArchiveManager.unzip(
            zipFile,
            tempFolder.newFolder("total"),
            ZipLimits(maxEntryBytes = 1_000, maxTotalBytes = 150)
        )

        assertTrue(result.isFailure)
    }

    private fun writeZip(name: String, entries: List<Pair<String, String>>): File {
        val zipFile = File(tempFolder.root, name)
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            entries.forEach { (entryName, content) ->
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return zipFile
    }
}
