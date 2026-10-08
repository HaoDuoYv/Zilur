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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
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
 * 从错误载荷里取服务端原话（OpenAI 兼容形态 `{"error":{"message":"..."}}`），
 * 取不到返回 null 由调用方兜底。
 */
private fun errorMessageOf(element: JsonElement): String? {
    val error = (element as? JsonObject)?.get("error") ?: return null
    return when (error) {
        is JsonObject -> (error["message"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        // 少数服务直接把错误写成字符串。
        is JsonPrimitive -> error.content.takeIf { it.isNotBlank() }
        else -> null
    }
}

/**
 * SSE 流聚合器：把逐行读到的 `data:` 分片拼成完整文本与工具调用，
 * 并**判定流是否正常结束**。
 *
 * 早先这段逻辑内联在 [LlmApiClient.chatStream] 里，收尾是 `readUtf8Line() ?: break` ——
 * 连接被提前关闭时循环"正常"退出，半截甚至空白文本被当成成功返回，落库一条
 * 0 长度 ASSISTANT 消息（用户看到空白气泡、且不报错）。现在把「结束信号」显式建模：
 * 收到 `data: [DONE]` 或某个分片带 `finish_reason` 才算正常结束，否则 [finish]
 * 抛错走上层的失败提示；正常结束但没有任何产出同样抛错。
 */
internal class SseStreamAssembler(private val json: Json) {

    private val text = StringBuilder()
    private val toolCalls = mutableMapOf<Int, ToolCallAccumulator>()

    /** 是否已收到正常结束信号（`[DONE]` 或 `finish_reason`）。 */
    var terminated: Boolean = false
        private set

    /** 服务端给出的结束原因（`stop` / `length` / `tool_calls`…），未结束时为 null。 */
    private var finishReason: String? = null

    /** 处理一行 SSE；返回本行新增的增量文本（无内容返回 null）。 */
    fun accept(line: String): String? {
        if (!line.startsWith("data:")) return null
        val data = line.removePrefix("data:").trim()
        if (data.isEmpty()) return null
        if (data == "[DONE]") {
            terminated = true
            return null
        }
        val element = runCatching { json.parseToJsonElement(data) }.getOrNull() ?: return null
        // HTTP 200 但流里塞的是错误对象（如免费额度耗尽）：把服务端原话透出去，
        // 否则会被当成「没有内容的正常响应」而吞掉。
        errorMessageOf(element)?.let { throw LlmApiException(it) }
        val chunk = runCatching { json.decodeFromJsonElement<ChatCompletionChunk>(element) }
            .getOrNull() ?: return null
        val choice = chunk.choices.firstOrNull() ?: return null
        choice.finishReason?.let {
            terminated = true
            finishReason = it
        }
        val delta = choice.delta
        delta.toolCalls?.forEach { call ->
            val acc = toolCalls.getOrPut(call.index) { ToolCallAccumulator(call.index) }
            call.id?.let { acc.id = it }
            call.function?.name?.let { acc.name = it }
            call.function?.arguments?.let { acc.arguments.append(it) }
        }
        val deltaText = delta.content
        if (deltaText.isNullOrEmpty()) return null
        text.append(deltaText)
        return deltaText
    }

    /** 收尾校验并返回聚合结果；流未正常结束、被截断或没有任何产出时抛 [LlmApiException]。 */
    fun finish(): StreamOutcome {
        if (!terminated) {
            throw LlmApiException("模型响应中断，请重试")
        }
        val outcome = StreamOutcome(
            text = text.toString(),
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
        // 被 max_tokens 截断的回答不可信：正文可能只说了一半，工具调用的 JSON 也可能是残的。
        // 半截回复留在会话里只会被当成完整结果看，不如不留 —— 给一条能照着做的提示。
        // 注意推理模型（reasoning_content）的思考 token 同样计入 max_tokens，
        // 「回答莫名其妙只有几个字」多半就是这里。
        if (finishReason == "length") {
            throw LlmApiException("回答超出 max_tokens 上限被截断，请在「AI 配置」里调大上限后重试")
        }
        // 正常结束但一字未出且没有工具调用 —— 不能当成成功，
        // 否则就是那条 0 长度的空白回复。
        if (outcome.text.isEmpty() && outcome.toolCalls.isEmpty()) {
            throw LlmApiException("模型没有返回任何内容，请重试")
        }
        return outcome
    }
}

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
     *
     * 结束判定交给 [SseStreamAssembler]：早先这里用 `readUtf8Line() ?: break` 收尾，
     * 连接被提前关闭会被当成正常读完，空文本走成功路径落库成空白回复；现在提前
     * 断开与空产出都会抛 [LlmApiException]，由上层提示失败（一字未出时还会走回退链）。
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
            val assembler = SseStreamAssembler(json)
            while (true) {
                val line = source.readUtf8Line() ?: break
                assembler.accept(line)?.let(onDelta)
                // 拿到结束信号就停：部分服务发完 [DONE] 不会立刻关连接。
                if (assembler.terminated) break
            }
            assembler.finish()
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
        val fallback = "模型请求失败（HTTP $code）"
        if (body.isNullOrBlank()) return fallback
        val element = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return fallback
        return errorMessageOf(element) ?: fallback
    }
}
