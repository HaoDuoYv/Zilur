package com.example.zhilu.domain.usecase

import android.content.Context
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.repository.MediaRepository
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.export.DtkExporter
import com.example.zhilu.export.collectReferencedMedia
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * 一次导出产出的东西：临时文件 + 两侧的计数（给提示文案用，免得调用方再查一遍库）。
 *
 * 调用方用完**必须删掉** [file]（它在 `cacheDir` 里）。
 */
data class ExportedBackup(
    val file: File,
    val noteCount: Int,
    val mediaCount: Int
)

/**
 * 把若干篇笔记打成一份 `.dtk`（在 `cacheDir` 里）。
 *
 * 抽出来是因为现在有**四个**出口要用同一份打包逻辑：单篇分享、设置页导出、
 * 首页选中项导出、云端备份上传。之前几处各写一遍"读媒体 → 收窄 → 导出"，
 * 每加一个出口就要再抄一遍 —— 而抄错的表现是"图片不全"这种静默问题。
 *
 * 只负责"产出文件"：SAF、分享、上传都是各自调用方的事。
 */
@Singleton
class ExportBackupUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteRepository: NoteRepository,
    private val mediaRepository: MediaRepository,
    private val mediaFileManager: MediaFileManager
) {

    /** 全量备份：库里所有笔记。 */
    suspend fun all(): Result<ExportedBackup> = try {
        val notes = when (val result = noteRepository.getAllNotes().first()) {
            is RepositoryResult.Success -> result.data
            is RepositoryResult.Error ->
                throw result.throwable ?: IllegalStateException(result.message)
        }
        of(notes)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** 指定笔记集合（首页多选导出用）。 */
    suspend fun of(notes: List<Note>): Result<ExportedBackup> = try {
        if (notes.isEmpty()) throw IllegalStateException("没有可导出的笔记")
        val media = when (val result = mediaRepository.getAllMedia()) {
            is RepositoryResult.Success -> result.data
            is RepositoryResult.Error ->
                throw result.throwable ?: IllegalStateException(result.message)
        }
        // 只打包被这些笔记真正引用的媒体：全库媒体入包会让备份里塞进孤儿图
        val referenced = collectReferencedMedia(notes, media)
        val file = DtkExporter(context, mediaFileManager)
            .exportNotes(notes, referenced)
            .getOrThrow()
        Result.success(ExportedBackup(file, notes.size, referenced.size))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
