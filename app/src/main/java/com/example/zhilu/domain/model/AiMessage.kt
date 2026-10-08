package com.example.zhilu.domain.model

import com.example.zhilu.domain.ai.model.AiRef

enum class AiRole { SYSTEM, USER, ASSISTANT, TOOL }

data class AiMessage(
    val id: Long = 0,
    val conversationId: Long = 0,
    val role: AiRole,
    val content: String,
    val images: List<String> = emptyList(),
    val fileText: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * 本条消息附带的引用（仅 USER 消息会用）。引用含**引用时刻的内容快照**，
     * 随消息一起落库（`ai_messages.refsJson`），请求组装时注入本条 user 消息。
     */
    val refs: List<AiRef> = emptyList(),
    /** 本条消息引用（回复）的**同一会话内**消息 id；null = 不是引用消息。 */
    val quotedMessageId: Long? = null
)

data class AiConversation(
    val id: Long = 0,
    val title: String = "新对话",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
