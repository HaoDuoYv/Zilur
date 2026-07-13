package com.example.zhilu.data.local.file

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ArchiveManager {

    fun zip(sourceDir: File, outputFile: File): Result<File> = runCatching {
        require(sourceDir.isDirectory) { "Source must be a directory" }
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isDirectory) return@forEach
                val entryName = file.relativeTo(sourceDir).invariantSeparatorsPath
                zos.putNextEntry(ZipEntry(entryName))
                FileInputStream(file).use { input ->
                    input.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
        outputFile
    }

    fun unzip(zipFile: File, outputDir: File): Result<File> = runCatching {
        require(zipFile.isFile) { "Zip file must exist" }
        outputDir.mkdirs()
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(outputDir, entry.name)
                if (entry.name.endsWith("/")) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output ->
                        zis.copyTo(output)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        outputDir
    }
}
