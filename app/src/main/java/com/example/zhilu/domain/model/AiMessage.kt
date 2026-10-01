package com.example.zhilu.domain.model

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
    val createdAt: Long = System.currentTimeMillis()
)

data class AiConversation(
    val id: Long = 0,
    val title: String = "新对话",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
