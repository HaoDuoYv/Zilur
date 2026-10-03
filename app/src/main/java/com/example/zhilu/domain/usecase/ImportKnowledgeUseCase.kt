package com.example.zhilu.domain.usecase

import android.content.Context
import android.net.Uri
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.local.file.ArchiveManager
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.repository.MediaRepository
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.export.JsonExporter
import com.example.zhilu.export.remapForInsert
import java.io.File
import java.io.FileOutputStream
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ImportKnowledgeUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteRepository: NoteRepository,
    private val mediaRepository: MediaRepository,
    private val mediaFileManager: MediaFileManager
) {

    data class Preview(
        val title: String,
        val blockCount: Int,
        val imageCount: Int
    )

    suspend fun parsePreview(uri: Uri): Result<Preview> = withContext(Dispatchers.IO) {
        runCatching {
            val tempZip = copyUriToTemp(uri)
            val unzipDir = File(context.cacheDir, "dtk_import_${System.currentTimeMillis()}").apply { mkdirs() }
            ArchiveManager.unzip(tempZip, unzipDir).getOrThrow()
            val noteJson = File(unzipDir, "note.json").readText()
            val note = JsonExporter.importNotes(noteJson).firstOrNull()
                ?: throw IllegalArgumentException("未找到笔记数据")
            val imagesDir = File(unzipDir, "media")
            val imageCount = if (imagesDir.exists()) imagesDir.listFiles()?.count { it.isFile } ?: 0 else 0
            Preview(
                title = note.title.ifBlank { "未命名笔记" },
                blockCount = note.contentBlocks.size,
                imageCount = imageCount
            )
        }
    }

    suspend fun import(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tempZip = copyUriToTemp(uri)
            val unzipDir = File(context.cacheDir, "dtk_import_${System.currentTimeMillis()}").apply { mkdirs() }
            ArchiveManager.unzip(tempZip, unzipDir).getOrThrow()

            val noteJson = File(unzipDir, "note.json").readText()
            val note = JsonExporter.importNotes(noteJson).firstOrNull()
                ?: throw IllegalArgumentException("未找到笔记数据")
            val dtkVersion = runCatching {
                JSONObject(noteJson).optInt("dtkVersion", 1)
            }.getOrDefault(1)
            if (dtkVersion > 1) throw IllegalArgumentException("不支持的 .dtk 版本")

            val imagesDir = File(unzipDir, "media")
            val mediaFiles = if (imagesDir.exists()) {
                imagesDir.listFiles()?.filter { it.isFile } ?: emptyList()
            } else emptyList()

            val importedMedia = mutableListOf<Media>()
            val uriMapping = mutableMapOf<String, String>()
            mediaFiles.forEach { file ->
                val importedUri = mediaFileManager.importUriToInternal(Uri.fromFile(file)).getOrThrow()
                val media = Media(uri = importedUri, size = file.length())
                val newId = when (val result = mediaRepository.insertMedia(media)) {
                    is RepositoryResult.Success -> result.data
                    is RepositoryResult.Error -> throw result.throwable ?: IllegalStateException("保存媒体失败")
                }
                importedMedia.add(media.copy(id = newId, uri = importedUri))
                uriMapping["media/${file.name}"] = importedUri
            }

            // 图片块：zip 里的 `media/xxx.jpg` → 刚写进内部存储的新 uri + 新 mediaId。
            // 必须遍历**所有**块（含卡片里的）—— 只看 note.contentBlocks 而丢掉 cards，
            // 正是"导入后小节结构全没了"的根因。
            fun fixImage(block: Block): Block {
                if (block.type != BlockType.IMAGE) return block
                val storedUri = uriMapping.entries.find { block.content.contains(it.key) }?.value
                    ?: block.content
                val mediaId = importedMedia.find { it.uri == storedUri }?.id ?: 0L
                return block.copy(content = ImageBlockContent.fromMedia(mediaId, storedUri))
            }

            val fixed = note.copy(
                cards = note.cards.map { card -> card.copy(blocks = card.blocks.map(::fixImage)) },
                blocks = note.blocks.map(::fixImage)
            )

            // 与 JSON 备份导入共用同一套 id / 层级重映射：卡片、分支父子、图片三者的引用
            // 必须成套重写（见 remapForInsert 的说明）。
            val noteToInsert = remapForInsert(fixed).copy(
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            when (val result = noteRepository.insertNote(noteToInsert)) {
                is RepositoryResult.Success -> Unit
                is RepositoryResult.Error -> throw result.throwable ?: IllegalStateException("保存笔记失败")
            }
        }
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
