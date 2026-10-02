package com.example.zhilu.ui.assistant

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhilu.ai.AiRefManager
import com.example.zhilu.ai.AiSubmitRequest
import com.example.zhilu.ai.AiTaskManager
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.datastore.UserPreferences
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.ai.model.AiRef
import com.example.zhilu.domain.ai.model.AiRefKind
import com.example.zhilu.domain.ai.model.AiTaskPhase
import com.example.zhilu.domain.repository.AiConversationRepository
import com.example.zhilu.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 助手页 ViewModel：只负责「提交任务 + 观察状态 + 渲染」，不再承载生成协程。
 *
 * 生成生命周期由进程级 [AiTaskManager] 管理，切换页面 / 离开助手页都不会中断。
 * - 消息列表由 Room Flow 驱动（[AiConversationRepository.getMessages]），作为唯一来源；
 * - 流式文本 / 工具状态 / 生成中态由 [AiTaskManager.state] 派生；
 * - 引用由 [AiRefManager] 跨页交接。
 */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val conversationRepository: AiConversationRepository,
    private val userPreferences: UserPreferences,
    private val mediaFileManager: MediaFileManager,
    private val taskManager: AiTaskManager,
    private val refManager: AiRefManager,
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var lastSubmittedText: String? = null
    private val surfacedErrorTaskIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            userPreferences.aiConfig.collect { config ->
                _uiState.update { it.copy(aiConfig = config) }
            }
        }
        viewModelScope.launch {
            conversationRepository.getConversations().collect { result ->
                when (result) {
                    is RepositoryResult.Success -> _uiState.update { it.copy(conversations = result.data) }
                    is RepositoryResult.Error -> Unit
                }
            }
        }
        observeMessages()
        observeTasks()
        observePendingRefs()
    }

    private fun observeMessages() {
        viewModelScope.launch {
            _uiState.map { it.currentConversationId }
                .distinctUntilChanged()
                .collectLatest { id ->
                    if (id == null) {
                        _uiState.update { it.copy(messages = emptyList()) }
                        return@collectLatest
                    }
                    conversationRepository.getMessages(id).collect { result ->
                        when (result) {
                            is RepositoryResult.Success -> _uiState.update { it.copy(messages = result.data) }
                            is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
                        }
                    }
                }
        }
    }

    private fun observeTasks() {
        viewModelScope.launch {
            taskManager.state.collect { taskState ->
                val currentId = _uiState.value.currentConversationId
                // 新建会话回流：当前无会话但有活跃任务已解析出会话 id 时自动采纳。
                val adoptId = if (currentId == null) {
                    taskState.tasks.lastOrNull { it.isActive && it.conversationId != 0L }?.conversationId
                } else {
                    null
                }
                val effectiveId = adoptId ?: currentId
                val activeTask = taskState.tasks.lastOrNull { it.isActive && it.conversationId == effectiveId }
                val failed = taskState.tasks.lastOrNull {
                    it.phase == AiTaskPhase.FAILED &&
                        it.error != null &&
                        it.id !in surfacedErrorTaskIds &&
                        (it.conversationId == effectiveId ||
                            (effectiveId == null && it.conversationId == 0L))
                }
                _uiState.update { s ->
                    var next = s
                    if (adoptId != null) next = next.copy(currentConversationId = adoptId)
                    next = next.copy(activeTask = activeTask)
                    if (failed != null) {
                        surfacedErrorTaskIds += failed.id
                        next = next.copy(error = failed.error, inputText = lastSubmittedText.orEmpty())
                    }
                    next
                }
            }
        }
    }

    private fun observePendingRefs() {
        viewModelScope.launch {
            refManager.pendingRefs.collect { refs ->
                if (refs.isNotEmpty()) {
                    _uiState.update { it.copy(attachedRefs = (it.attachedRefs + refs).distinct()) }
                    refManager.clearPending()
                }
            }
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /**
     * 附加图片：先把图复制到应用内部存储（file:// URI 持久有效），
     * 历史会话回看时缩略图不再因临时授权失效而无法加载。
     * 失败必须给出提示——静默失败会让用户以为「点了添加图片没反应」。
     */
    fun attachImages(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = withContext(Dispatchers.IO) {
                uris.map { uriString ->
                    uriString to mediaFileManager.importUriToInternal(Uri.parse(uriString)).getOrNull()
                }
            }
            val persisted = imported.mapNotNull { it.second }
            val failedCount = imported.size - persisted.size
            _uiState.update { state ->
                state.copy(
                    attachedImages = state.attachedImages + persisted,
                    error = when {
                        failedCount == 0 -> state.error
                        persisted.isEmpty() -> "图片添加失败，请换一张图片重试"
                        else -> "有 $failedCount 张图片添加失败"
                    }
                )
            }
        }
    }

    fun removeImage(index: Int) {
        _uiState.update { state ->
            if (index !in state.attachedImages.indices) state
            else state.copy(attachedImages = state.attachedImages.toMutableList().apply { removeAt(index) })
        }
    }

    fun attachFile(name: String, content: String) {
        _uiState.update { it.copy(attachedFile = AttachedFile(name = name, content = content)) }
    }

    fun removeFile() {
        _uiState.update { it.copy(attachedFile = null) }
    }

    fun removeRef(index: Int) {
        _uiState.update { state ->
            if (index !in state.attachedRefs.indices) state
            else state.copy(attachedRefs = state.attachedRefs.toMutableList().apply { removeAt(index) })
        }
    }

    fun openRefPicker() {
        viewModelScope.launch {
            when (val result = noteRepository.getAllNotes().first()) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(
                        refPickerNotes = result.data.map { note ->
                            AiRef(kind = AiRefKind.NOTE, noteId = note.id, title = note.title)
                        },
                        showRefPicker = true
                    )
                }
                is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    fun dismissRefPicker() {
        _uiState.update { it.copy(showRefPicker = false) }
    }

    fun addRef(ref: AiRef) {
        _uiState.update {
            it.copy(
                attachedRefs = (it.attachedRefs + ref).distinct(),
                showRefPicker = false
            )
        }
    }

    fun newConversation() {
        _uiState.update {
            it.copy(
                currentConversationId = null,
                messages = emptyList(),
                inputText = "",
                attachedImages = emptyList(),
                attachedFile = null,
                attachedRefs = emptyList(),
                activeTask = null,
                error = null
            )
        }
    }

    fun selectConversation(id: Long) {
        if (_uiState.value.currentConversationId == id) return
        _uiState.update {
            it.copy(
                currentConversationId = id,
                inputText = "",
                attachedImages = emptyList(),
                attachedFile = null,
                attachedRefs = emptyList(),
                activeTask = null
            )
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            conversationRepository.deleteConversation(id)
            if (_uiState.value.currentConversationId == id) {
                newConversation()
            }
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        val images = state.attachedImages
        val file = state.attachedFile
        val refs = state.attachedRefs
        if ((text.isEmpty() && images.isEmpty() && file == null && refs.isEmpty()) || state.isGenerating) return

        val config = state.aiConfig
        if (!config.isConfigured) {
            _uiState.update { it.copy(error = "请先在设置中配置 AI 助手") }
            return
        }

        lastSubmittedText = text
        taskManager.submit(
            AiSubmitRequest(
                text = text,
                images = images,
                fileName = file?.name,
                fileContent = file?.content,
                refs = refs,
                conversationId = state.currentConversationId
            )
        )
        _uiState.update {
            it.copy(
                inputText = "",
                attachedImages = emptyList(),
                attachedFile = null,
                attachedRefs = emptyList(),
                error = null
            )
        }
    }

    /**
     * 停止当前生成任务。输入区在生成中会把「发送」换成「停止」——
     * 任务现在能活到页面之外（前台服务保活），没有一个随时可点的出口就会变成只能等。
     */
    fun stopGeneration() {
        val taskId = _uiState.value.activeTask?.id ?: return
        taskManager.cancel(taskId)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
