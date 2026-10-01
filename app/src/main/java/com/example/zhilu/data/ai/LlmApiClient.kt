package com.example.zhilu.data.ai

import com.example.zhilu.data.ai.dto.ChatCompletionChunk
import com.example.zhilu.data.ai.dto.ChatCompletionRequest
import com.example.zhilu.data.ai.dto.ChatCompletionResponse
import com.example.zhilu.data.ai.dto.FunctionCallDto
import com.example.zhilu.data.ai.dto.ToolCallDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** LLM 调用异常，[code] 为 HTTP 状态码（网络层错误为 -1）。 */
class LlmApiException(message: String, val code: Int = -1) : Exception(message)

/** 流式调用的聚合结果：完整文本 + 可能存在的工具调用。 */
data class StreamOutcome(
    val text: String,
    val toolCalls: List<ToolCallDto>
)

/** SSE 流中按 index 聚合工具调用分片。 */
private class ToolCallAccumulator(
    val index: Int,
    var id: String = "",
    var name: String = "",
    val arguments: StringBuilder = StringBuilder()
)

/**
 * OpenAI 兼容的 LLM HTTP 客户端，纯网络职责：
 * 把 messages + tools 发送到 `{endpoint}/chat/completions`，返回完整响应或 SSE 流。
 * 不感知业务、不维护会话状态。
 */
@Singleton
class LlmApiClient @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        // 关键：编码带默认值的字段（如 ToolDto.type="function"），
        // 否则请求 JSON 缺失 "type"，OpenAI 兼容接口报 'type' is a required property - 'tools.0'。
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 非流式单轮调用（工具调用阶段用）。 */
    suspend fun chat(
        endpoint: String,
        apiKey: String,
        request: ChatCompletionRequest
    ): ChatCompletionResponse = withContext(Dispatchers.IO) {
        val httpRequest = buildRequest(endpoint, apiKey, request.copy(stream = false))
        client.newCall(httpRequest).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                throw LlmApiException(extractError(body, response.code), response.code)
            }
            json.decodeFromString<ChatCompletionResponse>(body ?: "")
        }
    }

    /**
     * 流式调用，逐段回调 [onDelta] 返回增量文本，同时聚合工具调用分片。
     * 返回完整文本与工具调用；二者在同一流里二选一为主（纯文本回答或 tool_calls）。
     */
    suspend fun chatStream(
        endpoint: String,
        apiKey: String,
        request: ChatCompletionRequest,
        onDelta: (String) -> Unit
    ): StreamOutcome = withContext(Dispatchers.IO) {
        val httpRequest = buildRequest(endpoint, apiKey, request.copy(stream = true))
        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string()
                throw LlmApiException(extractError(body, response.code), response.code)
            }
            val source = response.body?.source()
                ?: throw LlmApiException("模型未返回内容")
            val builder = StringBuilder()
            val toolCalls = mutableMapOf<Int, ToolCallAccumulator>()
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]" || data.isEmpty()) continue
                val chunk = runCatching {
                    json.decodeFromString<ChatCompletionChunk>(data)
                }.getOrNull() ?: continue
                val delta = chunk.choices.firstOrNull()?.delta ?: continue
                val text = delta.content
                if (!text.isNullOrEmpty()) {
                    builder.append(text)
                    onDelta(text)
                }
                delta.toolCalls?.forEach { call ->
                    val acc = toolCalls.getOrPut(call.index) { ToolCallAccumulator(call.index) }
                    call.id?.let { acc.id = it }
                    call.function?.name?.let { acc.name = it }
                    call.function?.arguments?.let { acc.arguments.append(it) }
                }
            }
            StreamOutcome(
                text = builder.toString(),
                toolCalls = toolCalls.values
                    .sortedBy { it.index }
                    .map { acc ->
                        ToolCallDto(
                            id = acc.id,
                            type = "function",
                            function = FunctionCallDto(name = acc.name, arguments = acc.arguments.toString())
                        )
                    }
            )
        }
    }

    private fun buildRequest(
        endpoint: String,
        apiKey: String,
        request: ChatCompletionRequest
    ): Request {
        val url = endpoint.trimEnd('/') + "/chat/completions"
        val body = json.encodeToString(request)
            .toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
    }

    private fun extractError(body: String?, code: Int): String {
        if (body.isNullOrBlank()) return "模型请求失败（HTTP $code）"
        return runCatching {
            val obj = json.parseToJsonElement(body)
            obj as? kotlinx.serialization.json.JsonObject
        }.getOrNull()?.get("error")?.let { error ->
            when (error) {
                is kotlinx.serialization.json.JsonObject ->
                    (error["message"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                        ?: "模型请求失败（HTTP $code）"
                else -> "模型请求失败（HTTP $code）"
            }
        } ?: "模型请求失败（HTTP $code）"
    }
}
