package com.example.zhilu.data.local.file

import java.io.File
import org.junit.Assert.assertEquals
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
}
