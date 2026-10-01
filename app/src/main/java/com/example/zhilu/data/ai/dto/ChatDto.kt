package com.example.zhilu.data.ai.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 纯文本 content 的构造器。 */
fun textContent(text: String): JsonElement = JsonPrimitive(text)

/** 多模态 content 的构造器：文本 + base64 data URL 图片列表。 */
fun multimodalContent(text: String, imageDataUrls: List<String>): JsonElement = buildJsonArray {
    add(buildJsonObject {
        put("type", "text")
        put("text", text)
    })
    imageDataUrls.forEach { url ->
        add(buildJsonObject {
            put("type", "image_url")
            put("image_url", buildJsonObject { put("url", url) })
        })
    }
}

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val stream: Boolean = false,
    val tools: List<ToolDto>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    val temperature: Double? = null
)

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: JsonElement? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDto>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null
)

@Serializable
data class ToolDto(
    val type: String = "function",
    val function: FunctionDto
)

@Serializable
data class FunctionDto(
    val name: String,
    val description: String,
    val parameters: JsonElement
)

@Serializable
data class ToolCallDto(
    val id: String,
    val type: String = "function",
    val function: FunctionCallDto
)

@Serializable
data class FunctionCallDto(
    val name: String,
    val arguments: String
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<ChoiceDto> = emptyList()
)

@Serializable
data class ChoiceDto(
    val message: ChatMessageDto,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChatCompletionChunk(
    val choices: List<ChunkChoiceDto> = emptyList()
)

@Serializable
data class ChunkChoiceDto(
    val delta: DeltaDto,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class DeltaDto(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDeltaDto>? = null
)

@Serializable
data class ToolCallDeltaDto(
    val index: Int = 0,
    val id: String? = null,
    val function: FunctionCallDeltaDto? = null
)

@Serializable
data class FunctionCallDeltaDto(
    val name: String? = null,
    val arguments: String? = null
)
