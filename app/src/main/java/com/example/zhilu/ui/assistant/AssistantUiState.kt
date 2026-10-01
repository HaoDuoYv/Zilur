package com.example.zhilu.ui.assistant

import com.example.zhilu.domain.model.AiConfig
import com.example.zhilu.domain.model.AiConversation
import com.example.zhilu.domain.model.AiMessage

/** 待发送的文本附件（txt / md）。 */
data class AttachedFile(
    val name: String,
    val content: String
)

data class AssistantUiState(
    val conversations: List<AiConversation> = emptyList(),
    val currentConversationId: Long? = null,
    val messages: List<AiMessage> = emptyList(),
    val inputText: String = "",
    val attachedImages: List<String> = emptyList(),
    val attachedFile: AttachedFile? = null,
    val aiConfig: AiConfig = AiConfig(),
    val isGenerating: Boolean = false,
    val streamingMessageId: Long? = null,
    val toolStatus: String? = null,
    val error: String? = null
)
