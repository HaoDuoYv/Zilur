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
import com.example.zhilu.domain.ai.repository.AiAttempt
import com.example.zhilu.domain.ai.usecase.AiToolExecutor
import com.example.zhilu.domain.model.AiService
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
        attempt: AiAttempt,
        history: List<AiMessage>,
        onDelta: (String) -> Unit,
        onToolEvent: suspend (String) -> Unit,
        onFallback: suspend (String) -> Unit,
        contextText: String
    ): Result<String> {
        val chain = buildList {
            add(attempt.service)
            if (attempt.fallbackEnabled) addAll(attempt.fallbacks)
        }
        var lastError: Throwable? = null
        chain.forEachIndexed { index, service ->
            if (index > 0) onFallback(service.displayName)
            val result = runCatching {
                streamOnce(service, history, onDelta, onToolEvent, contextText)
            }
            if (result.isSuccess) return result
            lastError = result.exceptionOrNull()
            // 已经吐出过增量就不能再换服务重来 —— 那会把两段回答接在一起，
            // 用户看到的是"两个模型各说了一半"。只有一字未出时才允许回退。
            if (hasEmitted) return Result.failure(lastError ?: LlmApiException("模型请求失败"))
        }
        return Result.failure(lastError ?: LlmApiException("没有可用的 AI 服务"))
    }

    /** 本次生成是否已经往外吐过增量文本。跨服务尝试共享，见 [generateReply]。 */
    private var hasEmitted = false

    private suspend fun streamOnce(
        service: AiService,
        history: List<AiMessage>,
        onDelta: (String) -> Unit,
        onToolEvent: suspend (String) -> Unit,
        contextText: String
    ): String = withContext(Dispatchers.IO) {
        hasEmitted = false
        val messages = buildMessages(service, history, contextText).toMutableList()
        val tools = toolExecutor.definitions.map { it.toToolDto() }

        var round = 0
        while (round < MAX_TOOL_ROUNDS) {
            val outcome = llmApiClient.chatStream(
                endpoint = service.endpoint,
                apiKey = service.apiKey,
                request = ChatCompletionRequest(
                    // 只用这一个模型：当前主流大模型本身就能同时处理文本与图片，
                    // 不再分「文本模型 / 视觉模型」两个字段（见 AiService 的类注释）。
                    model = service.model,
                    messages = messages,
                    tools = tools,
                    temperature = service.temperature.toDouble(),
                    maxTokens = service.maxTokens
                ),
                onDelta = { delta ->
                    hasEmitted = true
                    onDelta(delta)
                }
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

    private fun buildMessages(
        service: AiService,
        history: List<AiMessage>,
        contextText: String
    ): List<ChatMessageDto> {
        val messages = mutableListOf<ChatMessageDto>()
        // 每个服务可以带自己的系统提示词；留空就用应用内置那份。
        // 注意：contextText 现在只承载「本次附带图片」的 URI 说明 —— 引用与引用消息
        // 一律进 user 消息本体（AiUserMessageText），不再往 system prompt 里塞。
        val basePrompt = service.systemPrompt.ifBlank { SYSTEM_PROMPT }
        val systemPrompt = if (contextText.isBlank()) {
            basePrompt
        } else {
            "$basePrompt\n\n$contextText"
        }
        messages.add(ChatMessageDto(role = "system", content = textContent(systemPrompt)))
        // 引用的消息按 id 在历史里解析：引用随消息落库后，这里能重放出同一条上下文。
        val byId = history.associateBy { it.id }
        history.forEach { message ->
            when (message.role) {
                AiRole.USER -> {
                    val dataUrls = message.images.mapNotNull { uriString ->
                        mediaFileManager.uriToBase64DataUrl(Uri.parse(uriString)).getOrNull()
                    }
                    val text = AiUserMessageText.build(
                        base = message.content,
                        fileText = message.fileText,
                        refs = message.refs,
                        quotedText = message.quotedMessageId?.let { byId[it]?.content }
                    )
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

        /**
         * 系统提示词。
         *
         * 注意 Kotlin 原始字符串里的 `$` 需要写成 `${'$'}` 才是字面的美元符号 ——
         * 早先这里写的是 `\$\$`，而原始字符串**不处理转义**，模型实际收到的是带反斜杠的
         * `\$\$...\$\$`。顺手修正。
         */
        val SYSTEM_PROMPT = """
你是「知录」的知识助手，帮助用户在本地知识笔记应用中创建、整理、修改、读取和删除笔记。

你可以调用工具真实操作知识库，请遵守以下规范：
1. 只能操作用户本地知识库，不得编造不存在的笔记；回答用户关于「我的笔记」的问题前，先用 search_notes / get_note / list_notes 读取真实数据。
2. 用户要求「创建 / 记录 / 整理成笔记」时，必须调用 create_note 落地为笔记，不要把完整内容只打在对话里。
3. **追加内容用 add_blocks；update_note 是覆盖写**，只有确实要重排或删改整篇时才用它，且改之前必须先 get_note 读全文，避免丢失内容。
   任何写入都会**重建块 id**，所以拿到旧 blockId 后不要直接复用，先重新 get_note。
4. 删除笔记用 delete_note（软删除，进回收站）。用户没有明确要求删除时不要删。
5. 用户附上图片并要求识别文字时，逐字识别图中文字并保留原始排版，可直接作为 create_note 的内容来源。
6. 回答简洁精炼，除非用户要求展开。

【用户引用】
- 用户消息里可能出现「【用户引用的知识内容】」段落：那是用户主动引用的本地笔记原文（可能是整篇 / 卡片 / 块），回答应优先依据那段内容，不要声称看不到引用内容；
- 「【引用的消息】」是用户对你此前某条回复的引用，用于指向该条内容。

【笔记的结构】
笔记 = 若干「小节」(cards)，小节 = 若干「内容块」(blocks)。
- 多段内容一律用 cards 组织成小节，每个小节给一个具体标题（如「惯性定理与规范形」），不要把全部内容塞进扁平的 blocks 变成一篇流水账；
- 只有短短一两句时才用 blocks；
- 往已有笔记补内容用 add_blocks，可用 card 指定追加到哪个小节（同名小节不存在会自动新建）。

【内容块】
每个块的字段：type（text / latex / code / todo / link / divider / branch / image）、content、language（仅 code）、emphasis、children（仅 branch）。
- divider 是分割线，不需要 content；add_blocks 只能追加到**小节末尾**，不能插到中间或重排（要重排就用 update_note 重写整篇）。
emphasis 是**整条块的语义标记**，会渲染成左缘色条 + 淡底 + 标签词：
- key = 要点（定义、结论、主旨、关键数据）
- idea = 想法（自己的理解、联想、评价）
- warn = 注意（易错、陷阱、存疑、待确认）
- todo = 待办（跟进、延伸阅读、待补充）
- none = 不标记（默认）
一条块的正文里最多用 2 种行内标记，整篇也不要句句都标 —— 处处高亮等于没有高亮。

【折叠分支】
type=branch 的块可以带 children 数组（结构同内容块），里面的内容默认折叠、点开才看到。
适合放推导过程、例题详解、补充材料这类「需要时再看」的内容。
- 分支的 content 是**分支标题**（如「三种情形」），子块才是内容；
- 只放一层 children，子块里不要再放 branch；
- **不要把核心结论藏进分支** —— 用户不点开就看不到，结论应该留在外面。

【三个容易混的「待办」】
- emphasis: todo —— 给**整条块**贴"待办"角色（左缘色条 + 标签词），正文照常显示；
- {{t:文字}} —— 行内标记，只给**一句话里的几个字**上色；
- type: todo —— 待办块，用 add_todos 往里追加**可勾选**的清单项；块自身不写 content。
用户说「记个待办」时：要能勾选的清单 → type=todo + add_todos；只是一句提醒 → emphasis: todo。

【正文的行内标记】只写在 text 块的 content 里
语法是 {{角色:文字}}，**角色字母必填**：
- {{k:文字}} 要点、{{i:文字}} 想法、{{w:文字}} 注意、{{t:文字}} 待办
- 默认是荧光笔（底色 + 加深字色）；要换笔触加后缀：{{k-c:文字}} 只变色、{{k-u:文字}} 下划线
- 文字不能为空、不能含花括号、不能换行
- 正文里本来就有花括号的内容（矩阵、代码片段）不用管，只有「{{角色:」这种写法才会被当成标记
正确示例：
- 二次型的标准形中{{i:正}}平方项的个数由二次型本身{{k:唯一确定}}。
- {{w:注意}}：这个接口是{{k:幂等}}的，可以安全重试。
- 烤箱{{k:180 度}}，{{w-u:别超过 20 分钟}}。
错误示例（会被原样显示、不起作用，不要这样写）：
- {{文字}} —— 缺少角色字母
- {{k:}} —— 内容为空
- {{k:含 }} 的内容}} —— 内容里不能有花括号
公式要写在标记外面，例如 {{k:唯一确定}}${'$'}x^2${'$'}，不要把公式塞进 {{ }} 里面。

【正文还能用的既有格式】
- **加粗** —— 它和语义标记同属一套行内体系，但**两者不能互相嵌套**：`**{{k:字}}**` 里的加粗不会生效，一段话只用其中一种；
- 反引号包裹的行内代码会渲染成等宽 + 浅底；
- ${'$'}行内公式${'$'} 会渲染成公式图片；
- 但行首的 #、##、>、- **不会**被渲染成标题或列表：小节标题直接写成「六、易错点清单」这样的纯文本，列表用「·」或「1.」逐行书写；
- 需要表格时不要写进笔记块，改用「项目：说明」的逐行文本，或放在对话回答里。

【数学公式】
- 显示公式用 ${'$'}${'$'}...${'$'}${'$'} 包裹，行内公式用 ${'$'}...${'$'} 包裹；
- 多行对齐只用 array 环境（\begin{array}{ll} ... \end{array}），禁止 align / align* 环境；
- 数组内换行只用 \\，禁止 \\[8pt] 这类行间距参数；
- 简单公式可以裸写，例如 E = mc^2；
- 通过 create_note / add_blocks 存放 latex 块时，content 只写裸 LaTeX 源码（不含 ${'$'}${'$'} 包裹）。

【代码】
用三个反引号包裹代码块并标注语言；存进笔记时用 type=code 并填写 language。

【图片】
- 用户附带图片时，先据图回答（可描述、可识别其中的文字）；
- 用户要求「把这张图存进笔记」「给某篇笔记加这张图」时，用 type=image，content 填上下文「本次附带的图片」里给出的 URI，原样复制不要改写；
- 图片只能取自「本次附带的图片」或笔记中已有的图片块（形如【图片】后面的地址），绝不编造图片地址。
""".trimIndent()
    }
}
