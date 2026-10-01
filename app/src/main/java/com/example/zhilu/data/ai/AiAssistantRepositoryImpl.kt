package com.example.zhilu.data.ai

import android.net.Uri
import com.example.zhilu.data.ai.dto.ChatCompletionRequest
import com.example.zhilu.data.ai.dto.ChatMessageDto
import com.example.zhilu.data.ai.dto.FunctionDto
import com.example.zhilu.data.ai.dto.ToolDto
import com.example.zhilu.data.ai.dto.multimodalContent
import com.example.zhilu.data.ai.dto.textContent
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.ai.model.AiToolDefinition
import com.example.zhilu.domain.ai.repository.AiAssistantRepository
import com.example.zhilu.domain.ai.usecase.AiToolExecutor
import com.example.zhilu.domain.model.AiConfig
import com.example.zhilu.domain.model.AiMessage
import com.example.zhilu.domain.model.AiRole
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * AI 对话仓库实现：组装 system prompt 与历史消息，驱动 Function Calling 工具循环，
 * 最终以流式文本回答。纯文本对话首轮即流式返回；涉及工具时先执行工具再流式回答。
 * 支持多模态：USER 消息携带图片时转 base64 data URL，切换视觉模型识别。
 */
@Singleton
class AiAssistantRepositoryImpl @Inject constructor(
    private val llmApiClient: LlmApiClient,
    private val toolExecutor: AiToolExecutor,
    private val mediaFileManager: MediaFileManager
) : AiAssistantRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun generateReply(
        config: AiConfig,
        history: List<AiMessage>,
        onDelta: (String) -> Unit,
        onToolEvent: suspend (String) -> Unit,
        contextText: String
    ): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val hasImages = history.any { it.images.isNotEmpty() }
            val model = if (hasImages) config.visionModel.ifBlank { config.model } else config.model
            val messages = buildMessages(history, contextText).toMutableList()
            val tools = toolExecutor.definitions.map { it.toToolDto() }

            var round = 0
            while (round < MAX_TOOL_ROUNDS) {
                val outcome = llmApiClient.chatStream(
                    endpoint = config.endpoint,
                    apiKey = config.apiKey,
                    request = ChatCompletionRequest(
                        model = model,
                        messages = messages,
                        tools = tools
                    ),
                    onDelta = onDelta
                )
                if (outcome.toolCalls.isEmpty()) {
                    return@withContext outcome.text
                }

                // 工具调用阶段：追加 assistant 消息（含 tool_calls），执行工具后回填 tool 消息。
                messages.add(
                    ChatMessageDto(
                        role = "assistant",
                        content = null,
                        toolCalls = outcome.toolCalls
                    )
                )
            outcome.toolCalls.forEach { call ->
                onToolEvent(call.function.name)
                val result = toolExecutor.execute(call.function.name, call.function.arguments)
                    messages.add(
                        ChatMessageDto(
                            role = "tool",
                            content = JsonPrimitive(result),
                            toolCallId = call.id
                        )
                    )
                }
                round++
            }
            throw LlmApiException("工具调用轮次过多，已中止")
        }
    }

    private fun buildMessages(history: List<AiMessage>, contextText: String): List<ChatMessageDto> {
        val messages = mutableListOf<ChatMessageDto>()
        val systemPrompt = if (contextText.isBlank()) {
            SYSTEM_PROMPT
        } else {
            "$SYSTEM_PROMPT\n\n$contextText"
        }
        messages.add(ChatMessageDto(role = "system", content = textContent(systemPrompt)))
        history.forEach { message ->
            when (message.role) {
                AiRole.USER -> {
                    val dataUrls = message.images.mapNotNull { uriString ->
                        mediaFileManager.uriToBase64DataUrl(Uri.parse(uriString)).getOrNull()
                    }
                    val text = buildUserText(message)
                    messages.add(
                        ChatMessageDto(
                            role = "user",
                            content = if (dataUrls.isEmpty()) {
                                textContent(text)
                            } else {
                                multimodalContent(text, dataUrls)
                            }
                        )
                    )
                }
                AiRole.ASSISTANT ->
                    messages.add(ChatMessageDto(role = "assistant", content = textContent(message.content)))
                else -> Unit
            }
        }
        return messages
    }

    private fun buildUserText(message: AiMessage): String {
        val base = message.content
        val file = message.fileText
        if (file.isNullOrBlank()) return base
        return buildString {
            append(base)
            if (base.isNotBlank()) append("\n\n")
            append("【附件内容】\n")
            append(file)
        }
    }

    private fun AiToolDefinition.toToolDto(): ToolDto = ToolDto(
        type = "function",
        function = FunctionDto(
            name = name,
            description = description,
            parameters = json.parseToJsonElement(parametersJson)
        )
    )

    companion object {
        private const val MAX_TOOL_ROUNDS = 5

        val SYSTEM_PROMPT = """
你是「知录」的知识助手，帮助用户在本地知识笔记应用中创建、整理、修改和读取知识点。

你可以调用工具真实操作知识库，请遵守以下规范：
1. 只能操作用户本地知识库，不得编造不存在的笔记；回答用户关于「我的笔记」的问题前，先用 search_notes / get_note / list_notes 读取真实数据。
2. 用户要求「创建 / 记录 / 整理成笔记」时，必须调用 create_note 落地为笔记，不要把完整内容只打在对话里。
3. 修改已有笔记时，先 get_note 读取全文，再把完整内容与修改一并写回 update_note，避免丢失内容。
4. 用户附上图片并要求识别文字时，逐字识别图中文字并保留原始排版，可直接作为 create_note 的内容来源。
5. 回答简洁精炼，除非用户要求展开。

涉及数学公式时，使用 LaTeX 语法：
- 显示公式用 \$\$...\$\$ 包裹，行内公式用 \$...\$ 包裹；
- 多行对齐只用 array 环境（\begin{array}{ll} ... \end{array}），禁止 align / align* 环境；
- 数组内换行只用 \\，禁止 \\[8pt] 这类行间距参数；
- 简单公式可以裸写，例如 E = mc^2；
- 通过 create_note 的 blocks 存放公式时，content 只写裸 LaTeX 源码（不含 \$\$ 包裹）。

涉及代码时，用三个反引号包裹代码块，并在开头标注语言；通过 create_note 存放代码块时使用 type=code 并填写 language。

通过 create_note / update_note 写入笔记正文时，使用笔记的纯文本排版约定：
- 笔记正文不渲染 Markdown 标记，行首的 #、##、>、- 与行内 **加粗** 都会原样显示，请一律改用纯文本；
- 小节标题直接写成「六、易错点清单」这样的纯文本；列表用「·」或「1.」逐行书写；
- 需要表格时不要写进笔记块，改用「项目：说明」的逐行文本，或放在对话回答里。

图片：
- 用户附带图片时，先据图回答（可描述、可识别其中的文字）；
- 用户要求「把这张图存进笔记」「给某篇笔记加这张图」时，调用 create_note / update_note 并使用 type=image，content 填上下文「本次附带的图片」里给出的 URI，原样复制不要改写；
- 图片只能取自「本次附带的图片」或笔记中已有的图片块（形如【图片】后面的地址），绝不编造图片地址。
""".trimIndent()
    }
}
