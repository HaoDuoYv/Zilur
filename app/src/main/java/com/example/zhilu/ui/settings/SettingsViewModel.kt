package com.example.zhilu.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.data.datastore.AccentColor
import com.example.zhilu.data.datastore.ThemeMode
import com.example.zhilu.data.datastore.ThemePalette
import com.example.zhilu.data.datastore.UserPreferences
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.reminder.ReviewIntervals
import com.example.zhilu.domain.repository.MediaRepository
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.TagRepository
import com.example.zhilu.domain.repository.TodoRepository
import com.example.zhilu.export.JsonExporter
import com.example.zhilu.export.MarkdownExporter
import com.example.zhilu.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteRepository: NoteRepository,
    private val tagRepository: TagRepository,
    private val mediaRepository: MediaRepository,
    private val todoRepository: TodoRepository,
    private val mediaFileManager: MediaFileManager,
    private val userPreferences: UserPreferences,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshStats()
        observeStats()
        observePreferences()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            userPreferences.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            userPreferences.accentColor.collect { accent ->
                _uiState.update { it.copy(accentColor = accent) }
            }
        }
        viewModelScope.launch {
            userPreferences.themePalette.collect { palette ->
                _uiState.update { it.copy(themePalette = palette) }
            }
        }
        viewModelScope.launch {
            userPreferences.accessibleEmphasis.collect { enabled ->
                _uiState.update { it.copy(accessibleEmphasis = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferences.remindersEnabled.collect { enabled ->
                _uiState.update { it.copy(remindersEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferences.reviewIntervals.collect { intervals ->
                _uiState.update { it.copy(reviewIntervals = intervals) }
            }
        }
        viewModelScope.launch {
            userPreferences.aiSettings.collect { settings ->
                _uiState.update { it.copy(aiSettings = settings) }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferences.setThemeMode(mode)
        }
    }

    fun setAccentColor(accent: AccentColor) {
        viewModelScope.launch {
            userPreferences.setAccentColor(accent)
        }
    }

    fun setThemePalette(palette: ThemePalette) {
        viewModelScope.launch {
            userPreferences.setThemePalette(palette)
        }
    }

    fun setAccessibleEmphasis(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setAccessibleEmphasis(enabled)
        }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setRemindersEnabled(enabled)
            reminderScheduler.setEnabled(enabled)
        }
    }

    /**
     * 保存自定义复习间隔。
     *
     * 写入前再校验一次（界面已做即时校验，这里是最后一道闸）：非法值直接忽略，
     * 免得把脏数据写进 DataStore —— 虽然读回时会回落默认，但"看起来保存了、
     * 下次打开又变回去"对用户是更糟的谜题。
     */
    fun setReviewIntervals(days: List<Long>) {
        if (ReviewIntervals.validate(days) != null) return
        viewModelScope.launch {
            userPreferences.setReviewIntervals(days)
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            when (val result = noteRepository.getNoteCount()) {
                is RepositoryResult.Success -> _uiState.update { it.copy(noteCount = result.data) }
                is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
            }
        }
        viewModelScope.launch {
            when (val result = mediaRepository.getTotalSize()) {
                is RepositoryResult.Success -> _uiState.update { it.copy(totalMediaSize = result.data) }
                is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
            }
        }
        viewModelScope.launch {
            val activity = runCatching { loadActivity() }.getOrNull() ?: return@launch
            _uiState.update {
                it.copy(recordedDays = activity.totalDays, streakDays = activity.streak)
            }
        }
    }

    /** 「我的」页身份卡上那两个数字：共记录多少天、连续多少天。 */
    private data class Activity(val totalDays: Int, val streak: Int)

    private suspend fun loadActivity(): Activity {
        val notes = loadNotes()
        // 用「创建日 + 更新日」两个时间戳折算成"有记录的日子"：只看 createdAt 会漏掉
        // 老笔记被续写的那些天，只看 updatedAt 又会漏掉当天创建后就没再动过的笔记。
        val days = notes
            .flatMap { listOf(it.createdAt, it.updatedAt) }
            .map { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
            .toSet()
        if (days.isEmpty()) return Activity(0, 0)

        var streak = 0
        var cursor = LocalDate.now()
        // 今天还没记也不该把连击清零，所以从今天或昨天起算
        if (cursor !in days) cursor = cursor.minusDays(1)
        while (cursor in days) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return Activity(totalDays = days.size, streak = streak)
    }

    private fun observeStats() {
        viewModelScope.launch {
            tagRepository.getTagCount().collect { result ->
                when (result) {
                    is RepositoryResult.Success -> _uiState.update { it.copy(tagCount = result.data) }
                    is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
                }
            }
        }
        viewModelScope.launch {
            mediaRepository.getMediaCount().collect { result ->
                when (result) {
                    is RepositoryResult.Success -> _uiState.update { it.copy(mediaCount = result.data) }
                    is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
                }
            }
        }
    }

    fun exportJsonToUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            runCatching {
                val notes = loadNotes()
                val media = loadMedia()
                // 图片必须**内嵌字节**：只写 uri 的话，备份换台设备/清过数据后就再也恢复不出图。
                val mediaData = collectMediaData(media)
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(
                        JsonExporter.exportNotes(notes, media, mediaData).toByteArray(Charsets.UTF_8)
                    )
                } ?: error("无法写入所选文件")
                if (media.isEmpty()) {
                    "备份已导出"
                } else {
                    "备份已导出（含 ${mediaData.size}/${media.size} 张图片）"
                }
            }.fold(
                onSuccess = { message ->
                    _uiState.update { it.copy(isWorking = false, exportMessage = message) }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(isWorking = false, error = "导出失败：${throwable.message}")
                    }
                }
            )
        }
    }

    fun exportMarkdownToUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            runCatching {
                val notes = loadNotes()
                val media = loadMedia()
                val todosByNoteId = loadTodosByNoteId(notes)
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(MarkdownExporter.exportNotes(notes, media, todosByNoteId).toByteArray(Charsets.UTF_8))
                } ?: error("无法写入所选文件")
                "Markdown 已导出"
            }.fold(
                onSuccess = { message ->
                    _uiState.update { it.copy(isWorking = false, exportMessage = message) }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(isWorking = false, error = "导出失败：${throwable.message}")
                    }
                }
            )
        }
    }

    // 导入（JSON 备份 / .dtk）已迁移到**底栏中央 ＋** 的「新建 / 导入」弹层，
    // 由 `ui/create/CreateSheetViewModel` 负责；设置页只保留导出。
    // 逻辑本体在两处共用的用例里：`RestoreJsonBackupUseCase` 与 `ImportKnowledgeUseCase`。

    fun requestJsonExport() {
        _uiState.update { it.copy(exportMessage = "请选择 JSON 备份的保存位置") }
    }

    fun requestMarkdownExport() {
        _uiState.update { it.copy(exportMessage = "请选择 Markdown 的保存位置") }
    }

    fun updateNotificationPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(notificationPermissionGranted = granted) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(exportMessage = null, error = null) }
    }

    private suspend fun loadNotes(): List<Note> =
        when (val result = noteRepository.getAllNotes().first()) {
            is RepositoryResult.Success -> result.data
            is RepositoryResult.Error -> throw result.throwable ?: IllegalStateException(result.message)
        }

    private suspend fun loadMedia() =
        when (val result = mediaRepository.getAllMedia()) {
            is RepositoryResult.Success -> result.data
            is RepositoryResult.Error -> throw result.throwable ?: IllegalStateException(result.message)
        }

    private suspend fun loadTodosByNoteId(notes: List<Note>) =
        notes.associate { note ->
            val todos = when (val result = todoRepository.observeByNoteId(note.id).first()) {
                is RepositoryResult.Success -> result.data
                is RepositoryResult.Error -> throw result.throwable ?: IllegalStateException(result.message)
            }
            note.id to todos
        }

    /**
     * 把媒体读成 `id → base64 data URL`。单张读失败就跳过（不让一张坏图毁掉整个备份）。
     */
    private suspend fun collectMediaData(media: List<Media>): Map<Long, String> =
        withContext(Dispatchers.IO) {
            val cacheDir = File(context.cacheDir, "json_export_media").apply {
                deleteRecursively()
                mkdirs()
            }
            val result = media.mapNotNull { item ->
                val file = mediaFileManager.copyToCache(item, cacheDir).getOrNull()
                    ?: return@mapNotNull null
                val data = mediaFileManager.toBase64(file).getOrNull()
                    ?: return@mapNotNull null
                item.id to data
            }.toMap()
            cacheDir.deleteRecursively()
            result
        }
}
