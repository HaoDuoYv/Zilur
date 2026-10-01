package com.example.zhilu.ai

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.datastore.UserPreferences
import com.example.zhilu.domain.ai.model.AiRef
import com.example.zhilu.domain.ai.model.AiRefKind
import com.example.zhilu.domain.ai.model.AiTask
import com.example.zhilu.domain.ai.model.AiTaskPhase
import com.example.zhilu.domain.ai.model.AiTaskState
import com.example.zhilu.domain.ai.repository.AiAssistantRepository
import com.example.zhilu.domain.model.AiMessage
import com.example.zhilu.domain.model.AiRole
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.repository.AiConversationRepository
import com.example.zhilu.domain.repository.NoteRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 一次 AI 生成任务的提交参数（与 UI 层解耦，只传基本类型）。
 */
data class AiSubmitRequest(
    val text: String,
    val images: List<String> = emptyList(),
    val fileName: String? = null,
    val fileContent: String? = null,
    val refs: List<AiRef> = emptyList(),
    val conversationId: Long? = null
)

/**
 * 进程级 AI 任务管理器：承载生成协程的唯一事实源。
 *
 * 生成协程运行在**独立于 ViewModel 的 [scope]**（SupervisorJob + IO）上，
 * 因此切换页面 / 离开助手页都不会中断任务；UI 通过 [state] 观察任务进度、流式文本与占用锁。
 *
 * 状态机：QUEUED → RUNNING → (TOOL_CALLING ⇄ STREAMING) → SUCCEEDED / FAILED。
 */
@Singleton
class AiTaskManager @Inject constructor(
    private val aiAssistantRepository: AiAssistantRepository,
    private val conversationRepository: AiConversationRepository,
    private val noteRepository: NoteRepository,
    private val userPreferences: UserPreferences
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(AiTaskState())
    val state: StateFlow<AiTaskState> = _state.asStateFlow()

    /** 提交任务（非挂起）：立即入队并返回任务 id，生成在后台 scope 执行。 */
    fun submit(request: AiSubmitRequest): String {
        val taskId = UUID.randomUUID().toString()
        _state.update { s ->
            s.copy(
                tasks = s.tasks + AiTask(
                    id = taskId,
                    conversationId = request.conversationId ?: 0L,
                    phase = AiTaskPhase.QUEUED,
                    refs = request.refs
                )
            )
        }
        scope.launch { run(taskId, request) }
        return taskId
    }

    private suspend fun run(taskId: String, request: AiSubmitRequest) {
        val config = userPreferences.aiConfig.first()
        val contextText = resolveRefContext(request.refs) + resolveImageContext(request.images)

        // 1. 会话解析 + 用户消息落库。
        val conversationId = resolveConversation(request.conversationId, request.text, request.fileName)
        if (conversationId == 0L) {
            fail(taskId, "无法创建会话")
            return
        }
        update(taskId) { it.copy(conversationId = conversationId, phase = AiTaskPhase.RUNNING) }

        val userMessage = AiMessage(
            conversationId = conversationId,
            role = AiRole.USER,
            content = request.text,
            images = request.images,
            fileText = request.fileContent
        )
        val savedUser = when (val result = conversationRepository.addMessage(userMessage)) {
            is RepositoryResult.Success -> result.data
            is RepositoryResult.Error -> {
                fail(taskId, "保存消息失败")
                return
            }
        }

        // 2. 历史：仅保留 USER/ASSISTANT，且只为最新一条用户消息保留图片。
        val history = buildHistory(conversationId, savedUser)

        // 3. 生成（流式），全程在 app scope。
        val result = aiAssistantRepository.generateReply(
            config = config,
            history = history,
            onDelta = { delta ->
                update(taskId) { it.copy(phase = AiTaskPhase.STREAMING, streamText = it.streamText + delta) }
            },
            onToolEvent = { toolName ->
                update(taskId) { it.copy(phase = AiTaskPhase.TOOL_CALLING, toolName = toolName) }
                conversationRepository.addMessage(
                    AiMessage(
                        conversationId = conversationId,
                        role = AiRole.TOOL,
                        content = "调用了 $toolName",
                        toolName = toolName
                    )
                )
            },
            contextText = contextText
        )

        result.fold(
            onSuccess = { fullText ->
                // 先标记完成（移除流式合成气泡），再落库最终回复，避免短暂双份渲染。
                update(taskId) { it.copy(phase = AiTaskPhase.SUCCEEDED, toolName = null) }
                conversationRepository.addMessage(
                    AiMessage(
                        conversationId = conversationId,
                        role = AiRole.ASSISTANT,
                        content = fullText
                    )
                )
            },
            onFailure = { e ->
                fail(taskId, e.message ?: "生成失败，请重试")
            }
        )
    }

    private suspend fun resolveConversation(
        conversationId: Long?,
        text: String,
        fileName: String?
    ): Long {
        if (conversationId != null && conversationId > 0L) return conversationId
        val title = text.ifBlank {
            when {
                fileName != null -> "附件：$fileName"
                else -> "新对话"
            }
        }.take(20)
        return when (val result = conversationRepository.createConversation(title)) {
            is RepositoryResult.Success -> result.data.id
            is RepositoryResult.Error -> 0L
        }
    }

    private suspend fun buildHistory(conversationId: Long, latestUser: AiMessage): List<AiMessage> {
        val persisted = when (val result = conversationRepository.getMessages(conversationId).first()) {
            is RepositoryResult.Success -> result.data
            is RepositoryResult.Error -> emptyList()
        }
        val filtered = persisted.filter { it.role == AiRole.USER || it.role == AiRole.ASSISTANT }
        // 只为最新一条（刚落库的用户消息）保留图片，避免历史图片重复 base64 放大请求。
        return filtered.map { m ->
            if (m.id == latestUser.id) m else m.copy(images = emptyList())
        }
    }

    private suspend fun resolveRefContext(refs: List<AiRef>): String {
        if (refs.isEmpty()) return ""
        val sb = StringBuilder("【用户引用的知识内容】\n")
        refs.forEach { ref ->
            when (val result = noteRepository.getNoteById(ref.noteId)) {
                is RepositoryResult.Success -> {
                    val note = result.data
                    if (note == null) {
                        sb.append("- 笔记 ${ref.noteId} 不存在\n")
                    } else {
                        sb.append(formatRef(ref, note)).append("\n")
                    }
                }
                is RepositoryResult.Error ->
                    sb.append("- 读取笔记 ${ref.noteId} 失败：${result.message}\n")
            }
        }
        return sb.toString()
    }

    /**
     * 把本次附带的图片路径交代给模型，使「把这张图存进笔记」可行。
     * 图片在附加时已复制到应用内部存储，URI 长期有效，可直接作为 image 块内容。
     */
    private fun resolveImageContext(images: List<String>): String {
        if (images.isEmpty()) return ""
        return buildString {
            append("\n\n【本次附带的图片】用户这条消息附带了 ${images.size} 张图片：\n")
            images.forEachIndexed { index, uri ->
                append("图${index + 1}：$uri\n")
            }
            append("需要把图片写进笔记时，调用 create_note / update_note 并用 type=image、content 填上面的 URI（原样复制，不要改写）。")
        }
    }

    private fun formatRef(ref: AiRef, note: Note): String = when (ref.kind) {
        AiRefKind.NOTE -> "笔记(id=${note.id})《${note.title}》\n${formatBlocks(note.blocks)}"
        AiRefKind.CARD -> {
            val card = note.cards.firstOrNull { it.id == ref.cardId }
            if (card == null) "笔记(id=${note.id})的卡片 ${ref.cardId} 不存在"
            else "笔记(id=${note.id})卡片(id=${card.id})《${card.title}》\n${formatBlocks(card.blocks)}"
        }
        AiRefKind.BLOCK -> {
            val block = note.blocks.firstOrNull { it.id == ref.blockId }
            if (block == null) "笔记(id=${note.id})的块 ${ref.blockId} 不存在"
            else "笔记(id=${note.id})的块(id=${block.id})：\n${formatBlock(block)}"
        }
    }

    private fun formatBlocks(blocks: List<com.example.zhilu.domain.model.Block>): String =
        blocks.joinToString("\n\n") { formatBlock(it) }

    private fun formatBlock(block: com.example.zhilu.domain.model.Block): String = when (block.type) {
        BlockType.CODE -> "```${block.language.ifBlank { "text" }}\n${block.content}\n```"
        BlockType.LATEX -> "\$\$${block.content}\$\$"
        BlockType.LINK -> block.content
        BlockType.TODO -> "- [ ] ${block.content}"
        BlockType.DIVIDER -> "---"
        BlockType.BRANCH -> "【分支】${block.content}"
        BlockType.IMAGE -> "【图片】${ImageBlockContent.displayUri(block.content)}"
        else -> block.content
    }

    private fun update(taskId: String, transform: (AiTask) -> AiTask) {
        _state.update { s ->
            s.copy(
                tasks = s.tasks.map { if (it.id == taskId) transform(it) else it }
            )
        }
    }

    private fun fail(taskId: String, error: String) {
        update(taskId) { it.copy(phase = AiTaskPhase.FAILED, error = error) }
    }
}
