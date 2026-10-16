package com.example.zhilu.data.local.file

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 解包时的资源上限。
 *
 * 抽成参数而不是写死常量，是为了让测试能用极小阈值验证三道防线都在生效 ——
 * 真要构造一个 1 GiB 的 zip bomb 才能测「总量上限」的话，那条断言永远不会被写出来。
 */
internal data class ZipLimits(
    /** 条目数上限。正常 `.dtk` = 1 个 `note.json` + 每张图一个条目。 */
    val maxEntries: Int = 8_192,
    /** 单个条目解压后的上限。 */
    val maxEntryBytes: Long = 256L shl 20, // 256 MiB
    /** 全部条目解压总量上限：覆盖极端图库备份，挡住"几百 KB 压成几十 GB"。 */
    val maxTotalBytes: Long = 1L shl 30, // 1 GiB
)

/**
 * zip 打包 / 解包。
 *
 * **解包是唯一的不可信输入通道** —— `.dtk` 备份可以从任何地方来（聊天软件、网盘、
 * 别人发来的文件），所以 [unzip] 必须对恶意构造的包做防护：路径穿越与 zip bomb
 * 都能在没有这道防线时直接毁掉用户的数据目录。
 * 打包侧 `zip` 的输入全是本应用自己产出的文件，不需要对称的防护。
 */
object ArchiveManager {

    private const val COPY_BUFFER = 64 * 1024

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

    fun unzip(zipFile: File, outputDir: File): Result<File> =
        unzip(zipFile, outputDir, ZipLimits())

    /**
     * 解包到 [outputDir]。**不信任包内的任何条目名与长度声明**：
     *
     * - 条目名先把 `\` 归一成 `/`（恶意包用反斜杠绕过目录分隔符判断），
     *   再解析成规范路径，必须仍落在 [outputDir] 之内 —— 挡住 `../../` 之类越权写入；
     * - 条目数、单条目字节数、解压总量三重上限，且**手写 read 循环**而不是 `copyTo`：
     *   `copyTo` 只能事后知道写了多少，超限要能当场止损。
     *
     * 任何一条不过都直接失败（整包拒收），不做"跳过坏条目继续解"——
     * 一个被篡改的包，最好的处理是不相信它的任何部分。
     */
    internal fun unzip(zipFile: File, outputDir: File, limits: ZipLimits): Result<File> = runCatching {
        require(zipFile.isFile) { "Zip file must exist" }
        outputDir.mkdirs()
        val outRoot = outputDir.canonicalFile
        val outRootPrefix = outRoot.path + File.separator
        var entryCount = 0
        var totalBytes = 0L

        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entryCount++
                if (entryCount > limits.maxEntries) {
                    throw IOException("压缩包条目过多（超过 ${limits.maxEntries} 个）")
                }
                val name = entry.name.replace('\\', '/')
                if (name.isNotBlank()) {
                    val outFile = File(outRoot, name).canonicalFile
                    if (outFile.path != outRoot.path && !outFile.path.startsWith(outRootPrefix)) {
                        throw IOException("压缩包内含非法路径：${entry.name}")
                    }
                    if (name.endsWith("/")) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { output ->
                            val buffer = ByteArray(COPY_BUFFER)
                            var entryBytes = 0L
                            while (true) {
                                val read = zis.read(buffer)
                                if (read < 0) break
                                entryBytes += read
                                totalBytes += read
                                if (entryBytes > limits.maxEntryBytes) {
                                    throw IOException("压缩包内单个文件过大")
                                }
                                if (totalBytes > limits.maxTotalBytes) {
                                    throw IOException("压缩包解压后体积过大")
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        outputDir
    }
}
