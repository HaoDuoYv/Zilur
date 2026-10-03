package com.example.zhilu.domain.usecase

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.repository.MediaRepository
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.TagRepository
import com.example.zhilu.export.JsonExporter
import com.example.zhilu.export.remapForInsert
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 从 JSON 备份恢复全部笔记。
 *
 * 抽成用例是因为它现在有**两个入口**：设置页的「导入备份」与底栏 ＋ 的「导入备份」。
 * 复制一份的话，图片恢复、id 重映射这些细节迟早只在一个入口里修。
 *
 * 失败一律抛异常，由调用方 `runCatching` 兜住并转成用户能看懂的文案
 * （与项目里其它 suspend 用例的口径一致）。
 */
class RestoreJsonBackupUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteRepository: NoteRepository,
    private val tagRepository: TagRepository,
    private val mediaRepository: MediaRepository,
    private val mediaFileManager: MediaFileManager
) {

    data class Summary(val noteCount: Int, val mediaCount: Int)

    suspend operator fun invoke(uri: Uri): Summary = withContext(Dispatchers.IO) {
        val content = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: error("无法读取所选备份文件")
        invoke(content)
    }

    suspend operator fun invoke(json: String): Summary = withContext(Dispatchers.IO) {
        val backup = JsonExporter.importBackup(json)
        if (backup.notes.isEmpty()) error("备份里没有可恢复的笔记")

        // 图片先落盘再插笔记：remapForInsert 需要"旧媒体 id → 新 id"这张表
        val mediaIdMap = restoreMedia(backup.media, backup.mediaData)

        backup.notes.forEach { note ->
            val tags = resolveTags(note.tags)
            noteRepository.insertNote(
                remapForInsert(note, mediaIdMap).copy(tags = tags, deletedAt = null)
            )
        }
        Summary(noteCount = backup.notes.size, mediaCount = mediaIdMap.size)
    }

    /**
     * 恢复媒体。
     *
     * 备份里带 `data` 时**真的把图片写回内部存储**，并把媒体行指向新 uri —— 只重新插一行
     * 指向旧 uri 的记录等于没恢复（真机反馈过"图片无法导入"）。没有 `data` 的老备份
     * 退回旧行为（同机恢复仍可用）。
     */
    private suspend fun restoreMedia(media: List<Media>, mediaData: Map<Long, String>): Map<Long, Long> {
        val idMap = mutableMapOf<Long, Long>()
        media.forEach { item ->
            val restoredUri = mediaData[item.id]
                ?.takeIf { it.isNotBlank() }
                ?.let { restoreMediaFile(item, it) }
            val toInsert = if (restoredUri != null) item.copy(id = 0, uri = restoredUri) else item.copy(id = 0)
            val newId = when (val result = mediaRepository.insertMedia(toInsert)) {
                is RepositoryResult.Success -> result.data
                is RepositoryResult.Error -> throw result.throwable ?: IllegalStateException(result.message)
            }
            idMap[item.id] = newId
        }
        return idMap
    }

    /** base64 data URL → 内部存储里的图片，返回新的 uri；失败返回 null（不阻断整篇恢复）。 */
    private fun restoreMediaFile(item: Media, dataUrl: String): String? = runCatching {
        val base64 = dataUrl.substringAfter("base64,", dataUrl)
        val extension = dataUrl.substringAfter("data:", "")
            .substringBefore(";")
            .substringAfter('/', "png")
            .takeIf { it.isNotBlank() && it.length <= 5 && it.all(Char::isLetterOrDigit) }
            ?: "png"
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val temp = File(context.cacheDir, "restore_${item.id}.$extension")
        temp.writeBytes(bytes)
        val uri = mediaFileManager.importUriToInternal(Uri.fromFile(temp)).getOrThrow()
        temp.delete()
        uri
    }.getOrNull()

    /** 标签按名字复用已有的，避免每次恢复都造一批新标签。 */
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
}
