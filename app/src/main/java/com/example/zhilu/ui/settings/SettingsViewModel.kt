package com.example.zhilu.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.ai.LlmApiClient
import com.example.zhilu.data.ai.dto.ChatCompletionRequest
import com.example.zhilu.data.ai.dto.ChatMessageDto
import com.example.zhilu.data.ai.dto.textContent
import com.example.zhilu.domain.model.AiConfig
import com.example.zhilu.data.datastore.ThemeMode
import com.example.zhilu.data.datastore.UserPreferences
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.repository.MediaRepository
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.TagRepository
import com.example.zhilu.domain.repository.TodoRepository
import com.example.zhilu.domain.usecase.ImportKnowledgeUseCase
import com.example.zhilu.export.JsonExporter
import com.example.zhilu.export.MarkdownExporter
import com.example.zhilu.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val userPreferences: UserPreferences,
    private val reminderScheduler: ReminderScheduler,
    private val llmApiClient: LlmApiClient,
    private val importKnowledgeUseCase: ImportKnowledgeUseCase
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
            userPreferences.remindersEnabled.collect { enabled ->
                _uiState.update { it.copy(remindersEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferences.aiConfig.collect { config ->
                _uiState.update { it.copy(aiConfig = config) }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferences.setThemeMode(mode)
        }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setRemindersEnabled(enabled)
            reminderScheduler.setEnabled(enabled)
        }
    }

    fun updateAiConfig(config: AiConfig) {
        _uiState.update { it.copy(aiConfig = config, aiTestResult = null) }
    }

    fun saveAiConfig() {
        viewModelScope.launch {
            userPreferences.setAiConfig(_uiState.value.aiConfig)
            _uiState.update { it.copy(exportMessage = "AI 配置已保存") }
        }
    }

    fun testAiConnection() {
        val config = _uiState.value.aiConfig
        if (!config.isConfigured) {
            _uiState.update { it.copy(aiTestResult = "请先填写端点、Key 和模型") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(aiTestInProgress = true, aiTestResult = null) }
            val outcome = runCatching {
                llmApiClient.chat(
                    endpoint = config.endpoint,
                    apiKey = config.apiKey,
                    request = ChatCompletionRequest(
                        model = config.model,
                        messages = listOf(
                            ChatMessageDto(role = "user", content = textContent("你好，请回复「连接成功」"))
                        )
                    )
                )
            }
            _uiState.update {
                it.copy(
                    aiTestInProgress = false,
                    aiTestResult = outcome.fold(
                        onSuccess = { "连接成功" },
                        onFailure = { e -> "连接失败：${e.message ?: "未知错误"}" }
                    )
                )
            }
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
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(JsonExporter.exportNotes(notes, media).toByteArray(Charsets.UTF_8))
                } ?: error("Cannot open selected export file")
                "JSON backup exported"
            }.fold(
                onSuccess = { message ->
                    _uiState.update { it.copy(isWorking = false, exportMessage = message) }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(isWorking = false, error = throwable.message ?: "JSON export failed")
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
                } ?: error("Cannot open selected export file")
                "Markdown exported"
            }.fold(
                onSuccess = { message ->
                    _uiState.update { it.copy(isWorking = false, exportMessage = message) }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(isWorking = false, error = throwable.message ?: "Markdown export failed")
                    }
                }
            )
        }
    }

    fun importJsonFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            runCatching {
                val content = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                } ?: error("Cannot open selected backup")
                val backup = JsonExporter.importBackup(content)
                val mediaIdMap = importMedia(backup.media)
                backup.notes.forEach { note ->
                    val tags = importTags(note.tags)
                    val cleanBlocks = note.blocks.map { block ->
                        val contentValue = if (block.type == BlockType.IMAGE) {
                            val oldMediaId = ImageBlockContent.mediaId(block.content)
                            val oldUri = ImageBlockContent.displayUri(block.content)
                            oldMediaId?.let { mediaIdMap[it] }?.let { newMediaId ->
                                ImageBlockContent.fromMedia(newMediaId, oldUri)
                            } ?: block.content
                        } else {
                            block.content
                        }
                        block.copy(id = 0, noteId = 0, content = contentValue)
                    }
                    noteRepository.insertNote(
                        note.copy(
                            id = 0,
                            blocks = cleanBlocks,
                            tags = tags,
                            deletedAt = null
                        )
                    )
                }
                refreshStats()
                "Imported ${backup.notes.size} notes"
            }.fold(
                onSuccess = { message ->
                    _uiState.update { it.copy(isWorking = false, exportMessage = message) }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(isWorking = false, error = throwable.message ?: "JSON import failed")
                    }
                }
            )
        }
    }

    /** 解析 .dtk 文件并弹出预览确认；正式导入在用户确认后执行。 */
    fun parseImportPreview(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            importKnowledgeUseCase.parsePreview(uri)
                .onSuccess { preview ->
                    _uiState.update {
                        it.copy(isWorking = false, importPreview = preview, pendingImportUri = uri)
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            error = "无法解析文件：${throwable.message}"
                        )
                    }
                }
        }
    }

    fun confirmImport() {
        val uri = _uiState.value.pendingImportUri ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isWorking = true, importPreview = null, pendingImportUri = null)
            }
            importKnowledgeUseCase.import(uri)
                .onSuccess {
                    refreshStats()
                    _uiState.update { it.copy(isWorking = false, exportMessage = "导入成功") }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isWorking = false, error = "导入失败：${throwable.message}")
                    }
                }
        }
    }

    fun dismissImportPreview() {
        _uiState.update { it.copy(importPreview = null, pendingImportUri = null) }
    }

    fun requestJsonExport() {
        _uiState.update { it.copy(exportMessage = "Choose a JSON file location to export.") }
    }

    fun requestMarkdownExport() {
        _uiState.update { it.copy(exportMessage = "Choose a Markdown file location to export.") }
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

    private suspend fun importTags(tags: List<Tag>): List<Tag> = tags.map { tag ->
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

    private suspend fun importMedia(media: List<Media>): Map<Long, Long> {
        val idMap = mutableMapOf<Long, Long>()
        media.forEach { item ->
            val newId = when (val result = mediaRepository.insertMedia(item.copy(id = 0))) {
                is RepositoryResult.Success -> result.data
                is RepositoryResult.Error -> throw result.throwable ?: IllegalStateException(result.message)
            }
            idMap[item.id] = newId
        }
        return idMap
    }
}
