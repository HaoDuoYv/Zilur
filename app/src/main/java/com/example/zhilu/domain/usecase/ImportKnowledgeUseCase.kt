package com.example.zhilu.domain.usecase

import android.content.Context
import android.net.Uri
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.local.file.ArchiveManager
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.repository.MediaRepository
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.TagRepository
import com.example.zhilu.export.DtkExporter
import com.example.zhilu.export.JsonExporter
import com.example.zhilu.export.remapForInsert
import java.io.File
import java.io.FileOutputStream
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 导入 `.dtk`（zip：`note.json` + `media/`）。**这是全应用唯一的数据导入通道。**
 *
 * ## 图片是怎么接回来的
 *
 * 按 **mediaId** 精确 join，不靠文件名：备份里图片块的内容是 `"<源mediaId>|<源uri>"`
 * （见 `DtkExporter`），这里把 zip 内的 `media/media_<id>.<ext>` 逐个落盘建两张表
 * （旧 id → 新 id、旧 id → 新本机 uri），再交给 [remapForInsert] 改写块内容。
 *
 * 早先的实现是"拿 zip 里的文件名去 `block.content.contains(...)`"—— 内部存储路径形如
 * `.../files/images/img_xxx.png`，永远不含 `media/media_5.png`，于是永远匹配不上。
 *
 * ## 标签必须按名字认领
 *
 * 备份里的 `tag.id` 是**源设备**的主键，直接拿去插 `note_tags` 要么挂到本机同 id 的无关标签、
 * 要么留下一行指向不存在标签的悬空记录。所以这里统一走 [resolveTags]（按名查、无则建）。
 *
 * ## 多篇与失败
 *
 * 一份包可以有多篇（全量备份 / 选中项备份）。逐篇 `insertNote`（每篇自带事务），
 * **中途失败会带上已导入的进度**抛出 —— 用户至少知道库里现在是什么状态。
 *
 * 失败一律抛异常，由调用方 `runCatching` 兜住并转成用户能看懂的文案
 * （与项目里其它 suspend 用例的口径一致）。
 */
class ImportKnowledgeUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteRepository: NoteRepository,
    private val tagRepository: TagRepository,
    private val mediaRepository: MediaRepository,
    private val mediaFileManager: MediaFileManager
) {

    data class Preview(
        /** 包里的笔记篇数；> 1 时 UI 用「共 N 篇」的口径显示。 */
        val noteCount: Int,
        /** 首篇标题（单篇场景就是这一篇的标题）。 */
        val title: String,
        val blockCount: Int,
        val imageCount: Int
    )

    data class Summary(val noteCount: Int, val mediaCount: Int)

    suspend fun parsePreview(uri: Uri): Result<Preview> = withContext(Dispatchers.IO) {
        runCatching {
            withExtracted(uri) { unzipDir ->
                val backup = readBackup(unzipDir)
                val notes = backup.notes
                if (notes.isEmpty()) throw IllegalArgumentException("未找到笔记数据")
                Preview(
                    noteCount = notes.size,
                    title = notes.first().title.ifBlank { "未命名笔记" },
                    blockCount = notes.sumOf { it.contentBlocks.size },
                    imageCount = backup.media.size
                )
            }
        }
    }

    suspend fun import(uri: Uri): Result<Summary> = withContext(Dispatchers.IO) {
        runCatching {
            withExtracted(uri) { unzipDir ->
                val backup = readBackup(unzipDir)
                if (backup.notes.isEmpty()) throw IllegalArgumentException("未找到笔记数据")

                // 图片先落盘再插笔记：remapForInsert 需要"旧 id → 新 id / 新 uri"这两张表
                val mediaIdMap = mutableMapOf<Long, Long>()
                val mediaUriMap = mutableMapOf<Long, String>()
                backup.media.forEach { item ->
                    val source = File(unzipDir, item.uri)
                    // 包内缺这张图就跳过，不阻断整篇 —— 块仍带 mediaId，渲染侧有自己的兜底
                    if (!source.isFile) return@forEach
                    val newUri = mediaFileManager.importUriToInternal(Uri.fromFile(source)).getOrNull()
                        ?: return@forEach
                    val newId = when (
                        val result = mediaRepository.insertMedia(
                            Media(uri = newUri, size = source.length())
                        )
                    ) {
                        is RepositoryResult.Success -> result.data
                        is RepositoryResult.Error ->
                            throw result.throwable ?: IllegalStateException("保存媒体失败")
                    }
                    mediaIdMap[item.id] = newId
                    mediaUriMap[item.id] = newUri
                }

                var imported = 0
                val total = backup.notes.size
                backup.notes.forEach { note ->
                    val tags = resolveTags(note.tags)
                    val now = System.currentTimeMillis()
                    val toInsert = remapForInsert(note, mediaIdMap, mediaUriMap).copy(
                        tags = tags,
                        // 导入是一次全新的落库：创建时间也重置，避免备份里的旧时间戳
                        // 让新笔记排在列表末尾而"导入了却找不到"
                        createdAt = now,
                        updatedAt = now
                    )
                    when (val result = noteRepository.insertNote(toInsert)) {
                        is RepositoryResult.Success -> imported++
                        is RepositoryResult.Error -> throw IllegalStateException(
                            if (imported == 0) {
                                result.message
                            } else {
                                "已导入 $imported/$total 篇后失败：${result.message}"
                            }
                        )
                    }
                }
                Summary(noteCount = imported, mediaCount = mediaIdMap.size)
            }
        }
    }

    /**
     * 解压到临时目录并跑 [block]，无论成败都清掉临时件。
     *
     * 早先只有 `copyUriToTemp` 而没有清理，每导入一次就在 cache 里留一个包的副本。
     */
    private inline fun <T> withExtracted(uri: Uri, block: (File) -> T): T {
        val tempZip = copyUriToTemp(uri)
        val unzipDir = File(context.cacheDir, "dtk_import_${System.currentTimeMillis()}").apply {
            mkdirs()
        }
        try {
            ArchiveManager.unzip(tempZip, unzipDir).getOrThrow()
            return block(unzipDir)
        } finally {
            tempZip.delete()
            unzipDir.deleteRecursively()
        }
    }

    /** 读 `note.json` 并校验格式版本；高于本应用支持的版本直接拒绝。 */
    private fun readBackup(unzipDir: File): JsonExporter.Backup {
        val noteJson = File(unzipDir, "note.json").takeIf { it.isFile }?.readText()
            ?: throw IllegalArgumentException("压缩包里没有 note.json")
        val version = runCatching { JSONObject(noteJson).optInt("dtkVersion", 1) }.getOrDefault(1)
        if (version > DtkExporter.DTK_VERSION) {
            throw IllegalArgumentException("不支持的 .dtk 版本（v$version），请先升级「知录」")
        }
        return JsonExporter.importBackup(noteJson)
    }

    /** 标签按名字复用已有的，避免每次导入都造一批新标签（备份里的 id 是源设备的，不可直接用）。 */
    private suspend fun resolveTags(tags: List<Tag>): List<Tag> = tags.map { tag ->
        when (val existing = tagRepository.getTagByName(tag.name)) {
            is RepositoryResult.Success -> existing.data ?: insertTag(tag)
            is RepositoryResult.Error -> throw existing.throwable ?: IllegalStateException(existing.message)
        }
    }

    private suspend fun insertTag(tag: Tag): Tag =
        when (val result = tagRepository.insertTag(tag.copy(id = 0))) {
            is RepositoryResult.Success -> tag.copy(id = result.data)
            is RepositoryResult.Error -> throw result.throwable ?: IllegalStateException(result.message)
        }

    private fun copyUriToTemp(uri: Uri): File {
        val tempFile = File(context.cacheDir, "import_temp_${System.currentTimeMillis()}.dtk")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("无法读取所选文件")
        return tempFile
    }
}
