package com.example.zhilu.export

import android.content.Context
import com.example.zhilu.data.local.file.ArchiveManager
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Note
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `.dtk`（zip）导出：单篇分享、全量备份、选中项备份共用这一条路径。
 *
 * ## 图片是怎么跨设备的
 *
 * 图片块的内容是 `"<mediaId>|<uri>"`，`media[]` 列表里的条目把 uri 指向 zip 内的
 * `media/media_<id>.<ext>`。**关联键是 mediaId，不是文件名**：导入端按 mediaId 精确 join，
 * 重新落盘后换成本机 uri。
 *
 * 早先的实现只改写 `media[]` 的 uri、**不改写块内容**，导入端却靠
 * `block.content.contains("media/<文件名>")` 去认领 —— 内部存储路径是
 * `.../files/images/img_xxx.png`，永远不含 `media/media_5.png`，于是永远匹配不上：
 * mediaId 回落 0、uri 指向源设备的绝对路径。同机"看起来正常"，换台设备就是一片裂图。
 */
class DtkExporter(
    private val context: Context,
    private val mediaFileManager: MediaFileManager
) {

    companion object {
        /** 导入端只接受 ≤ 这个值的包。 */
        const val DTK_VERSION = 2
    }

    /**
     * 单篇导出（分享链用）。命名保留笔记标题，便于接收方辨认。
     */
    suspend fun exportNote(note: Note, media: List<Media> = emptyList()): Result<File> =
        exportNotes(listOf(note), media)

    /** 把若干篇笔记打成一个 `.dtk`；媒体跨篇共享，同一张图只写一份。 */
    suspend fun exportNotes(notes: List<Note>, media: List<Media> = emptyList()): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(notes.isNotEmpty()) { "没有可导出的笔记" }
                val exportDir = File(context.cacheDir, "dtk_export_${System.currentTimeMillis()}").apply {
                    deleteRecursively()
                    mkdirs()
                }
                val mediaDir = File(exportDir, "media").apply { mkdirs() }
                val mediaById = media.associateBy { it.id.toString() }
                // 按 id 去重：多篇 / 多块引用同一张图时只写一条媒体记录、只拷一份文件
                val exportedById = LinkedHashMap<Long, Media>()

                // 块内容必须在**收集完媒体之后**才能定稿（exportedById 是边遍历边填的），
                // 所以先遍历规范化块、再序列化。
                val prepared = notes.map { note ->
                    if (note.cards.isNotEmpty()) {
                        // 有卡片时 blocks 必须留空：Note.contentBlocks 的语义是"blocks 非空就只用 blocks"
                        note.copy(
                            blocks = emptyList(),
                            cards = note.cards.map { card ->
                                card.copy(
                                    blocks = card.blocks.sortedBy { it.sortOrder }.map {
                                        normalizeImage(it, mediaById, mediaDir, exportedById)
                                    }
                                )
                            }
                        )
                    } else {
                        note.copy(
                            blocks = note.blocks.sortedBy { it.sortOrder }.map {
                                normalizeImage(it, mediaById, mediaDir, exportedById)
                            }
                        )
                    }
                }

                // dtkVersion 必须写进 note.json：导入端按它拒收高版本。v2 起块内图片
                // 一律携带 mediaId（不再依赖文件名巧合），并支持一份多篇 ——
                // 老版本 App 读到 v2 会**明确拒绝**，而不是"多篇静默只导第一篇"。
                val json = JsonExporter.exportNotes(
                    notes = prepared,
                    media = exportedById.values.toList(),
                    dtkVersion = DTK_VERSION
                )
                File(exportDir, "note.json").writeText(json)

                val dtkFile = File(context.cacheDir, fileNameFor(notes))
                ArchiveManager.zip(exportDir, dtkFile).getOrThrow()
                exportDir.deleteRecursively()
                dtkFile
            }
        }

    /**
     * 把图片块的内容规范化成 `fromMedia(mediaId, 该媒体的本机 uri)`。
     *
     * 这一步顺带修掉「AI 早期写入的裸 URI 图片块」：它们没有 mediaId 前缀，
     * 不规范化的话导入端按 mediaId 关联时会整块漏掉。
     */
    private fun normalizeImage(
        block: Block,
        mediaById: Map<String, Media>,
        mediaDir: File,
        exportedById: LinkedHashMap<Long, Media>
    ): Block {
        if (block.type != BlockType.IMAGE) return block
        // resolveMedia：优先按块里登记的 mediaId 查，查不到再按内嵌 URI 反查
        val target = ImageBlockContent.resolveMedia(block.content, mediaById) ?: return block
        if (target.id !in exportedById) {
            val copied = mediaFileManager.copyToCache(target, mediaDir).getOrNull() ?: return block
            exportedById[target.id] = target.copy(uri = "media/${copied.name}")
        }
        return block.copy(content = ImageBlockContent.fromMedia(target.id, target.uri))
    }

    /** 单篇用标题（清洗掉文件名非法字符），多篇用统一的备份名。 */
    private fun fileNameFor(notes: List<Note>): String {
        if (notes.size != 1) return "zhilu-backup.dtk"
        val title = notes.first().title.trim()
            .take(40)
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_")
        return if (title.isBlank()) "zhilu-backup.dtk" else "$title.dtk"
    }
}
