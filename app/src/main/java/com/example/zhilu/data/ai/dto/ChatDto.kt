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
    /**
     * 声明序即编码序，此处按 OpenAI「How prompt caching works」的协议渲染序排列：
     * Tools → messages。服务端解析 JSON 后按自身 schema 组装 prompt，字段序本身不影响
     * 前缀缓存；但对"直接拼接 JSON 造 prompt"的中转站实现，固定序优于随机序（防御性对齐）。
     */
    val tools: List<ToolDto>? = null,
    val stream: Boolean = false,
    val messages: List<ChatMessageDto>,
    @SerialName("tool_choice") val toolChoice: String? = null,
    val temperature: Double? = null,
    /** 上限。OpenAI 兼容接口统一叫 `max_tokens`（`max_completion_tokens` 是 OpenAI 较新的别名，兼容面更窄）。 */
    @SerialName("max_tokens") val maxTokens: Int? = null,
    /**
     * 流式请求默认不返回 usage；显式 include_usage 后服务端在流末尾补一个
     * `choices=[]` + `usage` 的分片（见 [StreamOptionsDto]）。
     */
    @SerialName("stream_options") val streamOptions: StreamOptionsDto? = null
)

@Serializable
data class StreamOptionsDto(
    @SerialName("include_usage") val includeUsage: Boolean = true
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
    val choices: List<ChunkChoiceDto> = emptyList(),
    /**
     * 仅当请求带 `stream_options.include_usage` 时才出现：
     * 流末尾补发一个 `choices=[]` + `usage` 的分片（DeepSeek/OpenAI 同一形态）。
     */
    val usage: UsageDto? = null
)

/**
 * 用量与缓存命中观测（P4）。
 *
 * 两家字段不同形：DeepSeek 平铺 `prompt_cache_hit_tokens`；
 * OpenAI 嵌套 `prompt_tokens_details.cached_tokens` —— 统一由 [effectiveCachedTokens] 取值。
 */
@Serializable
data class UsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("prompt_cache_hit_tokens") val cacheHitTokens: Int? = null,
    @SerialName("prompt_cache_miss_tokens") val cacheMissTokens: Int? = null,
    @SerialName("prompt_tokens_details") val promptTokensDetails: PromptTokensDetailsDto? = null
) {
    @Serializable
    data class PromptTokensDetailsDto(
        @SerialName("cached_tokens") val cachedTokens: Int? = null
    )

    /** 统一取值：优先 DeepSeek 平铺字段，回退 OpenAI 嵌套字段，都无则 0。 */
    val effectiveCachedTokens: Int
        get() = cacheHitTokens ?: promptTokensDetails?.cachedTokens ?: 0
}

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
