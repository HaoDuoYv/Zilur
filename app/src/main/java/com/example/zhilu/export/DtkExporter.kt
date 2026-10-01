package com.example.zhilu.export

import android.content.Context
import com.example.zhilu.data.local.file.ArchiveManager
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Note
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DtkExporter(
    private val context: Context,
    private val mediaFileManager: MediaFileManager
) {

    suspend fun exportNote(note: Note, media: List<Media> = emptyList()): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val exportDir = File(context.cacheDir, "dtk_export_${System.currentTimeMillis()}").apply {
                    deleteRecursively()
                    mkdirs()
                }
                val mediaDir = File(exportDir, "media").apply { mkdirs() }
                val mediaById = media.associateBy { it.id.toString() }
                val exportedMedia = mutableListOf<Media>()

                val blocks = note.cards.takeIf { it.isNotEmpty() }
                    ?.flatMap { it.blocks }
                    ?.sortedBy { it.sortOrder }
                    ?: note.blocks.sortedBy { it.sortOrder }

                blocks.forEach { block ->
                    if (block.type != BlockType.IMAGE) return@forEach
                    // 统一走 resolveMedia：mediaId 缺失时会按 URI 反查，
                    // 否则 AI 写入的裸 URI 图片块会在这里被静默跳过，导出包里就丢了图。
                    val targetMedia = ImageBlockContent.resolveMedia(block.content, mediaById)
                        ?: return@forEach
                    val copied = mediaFileManager.copyToCache(targetMedia, mediaDir).getOrNull()
                        ?: return@forEach
                    exportedMedia.add(targetMedia.copy(uri = "media/${copied.name}"))
                }

                val dtkNote = note.copy(blocks = blocks)
                val json = JsonExporter.exportNote(dtkNote, exportedMedia)
                File(exportDir, "note.json").writeText(json)

                val dtkFile = File(context.cacheDir, "${note.title.ifBlank { "export" }}.dtk")
                ArchiveManager.zip(exportDir, dtkFile).getOrThrow()
                exportDir.deleteRecursively()
                dtkFile
            }
        }
}
