package com.example.zhilu.data.local.mapper

import com.example.zhilu.data.local.entity.AiConversationEntity
import com.example.zhilu.data.local.entity.AiMessageEntity
import com.example.zhilu.domain.ai.model.AiRef
import com.example.zhilu.domain.model.AiConversation
import com.example.zhilu.domain.model.AiMessage
import com.example.zhilu.domain.model.AiRole
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object AiMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun toDomain(entity: AiConversationEntity): AiConversation = AiConversation(
        id = entity.id,
        title = entity.title,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )

    fun toEntity(domain: AiConversation): AiConversationEntity = AiConversationEntity(
        id = domain.id,
        title = domain.title,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt
    )

    fun toDomain(entity: AiMessageEntity): AiMessage = AiMessage(
        id = entity.id,
        conversationId = entity.conversationId,
        role = runCatching { AiRole.valueOf(entity.role) }.getOrDefault(AiRole.USER),
        content = entity.content,
        images = entity.imagesJson?.let { raw ->
            runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList(),
        fileText = entity.fileText,
        toolCallId = entity.toolCallId,
        toolName = entity.toolName,
        createdAt = entity.createdAt,
        // 解析失败按「没有引用」处理：引用是增强信息，坏一行 JSON 不该把整条消息读崩。
        refs = entity.refsJson?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { json.decodeFromString<List<AiRef>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList(),
        quotedMessageId = entity.quotedMessageId
    )

    fun toEntity(domain: AiMessage): AiMessageEntity = AiMessageEntity(
        id = domain.id,
        conversationId = domain.conversationId,
        role = domain.role.name,
        content = domain.content,
        imagesJson = if (domain.images.isEmpty()) null else json.encodeToString(domain.images),
        fileText = domain.fileText,
        toolCallId = domain.toolCallId,
        toolName = domain.toolName,
        createdAt = domain.createdAt,
        refsJson = if (domain.refs.isEmpty()) null else json.encodeToString(domain.refs),
        quotedMessageId = domain.quotedMessageId
    )
}
