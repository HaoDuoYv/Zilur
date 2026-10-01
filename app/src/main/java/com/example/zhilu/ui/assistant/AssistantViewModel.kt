package com.example.zhilu.ui.assistant

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.datastore.UserPreferences
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.ai.repository.AiAssistantRepository
import com.example.zhilu.domain.model.AiMessage
import com.example.zhilu.domain.model.AiRole
import com.example.zhilu.domain.repository.AiConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
class AssistantViewModel @Inject constructor(
    private val aiAssistantRepository: AiAssistantRepository,
    private val conversationRepository: AiConversationRepository,
    private val userPreferences: UserPreferences,
    private val mediaFileManager: MediaFileManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var tempMessageId = 0L

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
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /**
     * 附加图片：先把图复制到应用内部存储（file:// URI 持久有效），
     * 历史会话回看时缩略图不再因临时授权失效而无法加载。
     */
    fun attachImages(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val persisted = withContext(Dispatchers.IO) {
                uris.mapNotNull { uriString ->
                    runCatching {
                        mediaFileManager.importUriToInternal(Uri.parse(uriString)).getOrNull()
                    }.getOrNull()
                }
            }
            if (persisted.isNotEmpty()) {
                _uiState.update { it.copy(attachedImages = it.attachedImages + persisted) }
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

    fun newConversation() {
        _uiState.update {
            it.copy(
                currentConversationId = null,
                messages = emptyList(),
                inputText = "",
                attachedImages = emptyList(),
                attachedFile = null,
                toolStatus = null
            )
        }
    }

    fun selectConversation(id: Long) {
        if (_uiState.value.currentConversationId == id) return
        _uiState.update { it.copy(currentConversationId = id) }
        viewModelScope.launch {
            when (val result = conversationRepository.getMessages(id).first()) {
                is RepositoryResult.Success -> _uiState.update { it.copy(messages = result.data) }
                is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
            }
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
        if ((text.isEmpty() && images.isEmpty() && file == null) || state.isGenerating) return

        val config = state.aiConfig
        if (!config.isConfigured) {
            _uiState.update { it.copy(error = "请先在设置中配置 AI 助手") }
            return
        }

        viewModelScope.launch {
            val title = text.ifBlank {
                when {
                    file != null -> "附件：${file.name}"
                    images.isNotEmpty() -> "识别图片"
                    else -> "新对话"
                }
            }.take(20)
            val conversationId = ensureConversation(title) ?: return@launch

            val userMessage = AiMessage(
                id = nextTempId(),
                conversationId = conversationId,
                role = AiRole.USER,
                content = text,
                images = images,
                fileText = file?.content
            )
            conversationRepository.addMessage(userMessage.copy(id = 0))

            // 只有最新一条消息重发图片：历史图片已识别过，重复 base64 会白白放大请求体积。
            val historyBefore = _uiState.value.messages
                .filter { it.role == AiRole.USER || it.role == AiRole.ASSISTANT }
                .map { it.copy(images = emptyList()) } + userMessage

            val assistantTempId = nextTempId()
            val assistantTemp = AiMessage(
                id = assistantTempId,
                conversationId = conversationId,
                role = AiRole.ASSISTANT,
                content = ""
            )

            _uiState.update {
                it.copy(
                    inputText = "",
                    attachedImages = emptyList(),
                    attachedFile = null,
                    isGenerating = true,
                    streamingMessageId = assistantTempId,
                    toolStatus = null,
                    messages = it.messages + userMessage + assistantTemp,
                    error = null
                )
            }

            val result = aiAssistantRepository.generateReply(
                config = config,
                history = historyBefore,
                onDelta = { delta ->
                    _uiState.update { s ->
                        s.copy(
                            messages = s.messages.map { msg ->
                                if (msg.id == assistantTempId) {
                                    msg.copy(content = msg.content + delta)
                                } else {
                                    msg
                                }
                            }
                        )
                    }
                },
                onToolEvent = { toolName ->
                    _uiState.update { it.copy(toolStatus = toolName) }
                    val toolMessage = AiMessage(
                        id = nextTempId(),
                        conversationId = conversationId,
                        role = AiRole.TOOL,
                        content = "调用了 $toolName",
                        toolName = toolName
                    )
                    _uiState.update { it.copy(messages = it.messages + toolMessage) }
                    viewModelScope.launch {
                        conversationRepository.addMessage(toolMessage.copy(id = 0))
                    }
                }
            )

            result.fold(
                onSuccess = { fullText ->
                    conversationRepository.addMessage(
                        AiMessage(
                            conversationId = conversationId,
                            role = AiRole.ASSISTANT,
                            content = fullText
                        )
                    )
                    _uiState.update { it.copy(isGenerating = false, streamingMessageId = null, toolStatus = null) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            streamingMessageId = null,
                            toolStatus = null,
                            // 恢复输入内容，便于修改后重发，不用重新打字
                            inputText = text,
                            error = e.message ?: "生成失败，请重试"
                        )
                    }
                }
            )
        }
    }

    private suspend fun ensureConversation(title: String): Long? {
        val existing = _uiState.value.currentConversationId
        if (existing != null) return existing
        return when (val result = conversationRepository.createConversation(title)) {
            is RepositoryResult.Success -> {
                val id = result.data.id
                _uiState.update { it.copy(currentConversationId = id) }
                id
            }
            is RepositoryResult.Error -> {
                _uiState.update { it.copy(error = result.message) }
                null
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun nextTempId(): Long = --tempMessageId
}
