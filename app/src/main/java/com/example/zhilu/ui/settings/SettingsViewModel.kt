package com.example.zhilu.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.datastore.AccentColor
import com.example.zhilu.data.datastore.ThemeMode
import com.example.zhilu.data.datastore.ThemePalette
import com.example.zhilu.data.datastore.UserPreferences
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.reminder.ReviewIntervals
import com.example.zhilu.domain.reminder.ReviewQueueClassifier
import com.example.zhilu.domain.repository.CloudRepository
import com.example.zhilu.domain.repository.MediaRepository
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.ReviewRepository
import com.example.zhilu.domain.repository.TagRepository
import com.example.zhilu.domain.repository.TodoRepository
import com.example.zhilu.domain.usecase.ExportBackupUseCase
import com.example.zhilu.export.MarkdownExporter
import com.example.zhilu.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteRepository: NoteRepository,
    private val tagRepository: TagRepository,
    private val mediaRepository: MediaRepository,
    private val todoRepository: TodoRepository,
    private val exportBackup: ExportBackupUseCase,
    private val cloudRepository: CloudRepository,
    private val reviewRepository: ReviewRepository,
    private val userPreferences: UserPreferences,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** 复习分区口径（今天 0 点）与复习中心共用，直接实例化——它没有任何依赖。 */
    private val queueClassifier = ReviewQueueClassifier()

    init {
        refreshStats()
        observeStats()
        observePreferences()
        observeReviewPending()
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
        // 云端只读一行摘要（"未连接 / 已登录 xxx"），不在这里发任何网络请求 ——
        // 真正的连接状态与备份信息在 `ui/cloud/CloudScreen` 里
        viewModelScope.launch {
            cloudRepository.settings.collect { settings ->
                _uiState.update { it.copy(cloudSettings = settings) }
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

    /**
     * 「我的」复习中心那一行右侧的「待处理 N」角标。
     *
     * 口径与复习中心完全一致：`overdue + today`（都按今天 0 点分界）。
     * 用同一条 [ReviewQueueClassifier] 而不是在这里另写一份判定 —— 两处各算各的
     * 迟早会在跨零点时给出对不上的数字。
     */
    private fun observeReviewPending() {
        viewModelScope.launch {
            reviewRepository.observePlans().collect { result ->
                when (result) {
                    is RepositoryResult.Success -> {
                        val queue = queueClassifier.classify(result.data, startOfToday())
                        _uiState.update {
                            it.copy(reviewPendingCount = queue.overdue.size + queue.today.size)
                        }
                    }
                    is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
                }
            }
        }
    }

    /** 今天 0 点：复习是"天"粒度，分区以它为界（见 `ReviewQueueClassifier`）。 */
    private fun startOfToday(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * 导出全量备份为 `.dtk`（zip：`note.json` + `media/`）。
     *
     * 打包本身走 [ExportBackupUseCase] —— 与首页选中项导出、云端备份上传、单篇分享
     * 共用同一条路径，图片引用只有一种编码方式（按 mediaId join）。
     */
    fun exportDtkToUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            runCatching {
                val backup = exportBackup.all().getOrThrow()
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        backup.file.inputStream().use { it.copyTo(output) }
                    } ?: error("无法写入所选文件")
                } finally {
                    // 产物在 cacheDir：不删的话每次导出都在里面留一份完整备份
                    backup.file.delete()
                }
                "备份已导出（${backup.noteCount} 篇笔记" +
                    if (backup.mediaCount == 0) "）" else "，含 ${backup.mediaCount} 张图片）"
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

    // 导入（只有 .dtk）在**底栏中央 ＋** 的「新建 / 导入」弹层里，由
    // `ui/create/CreateSheetViewModel` + `ImportKnowledgeUseCase` 负责；设置页只保留导出。

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
}
