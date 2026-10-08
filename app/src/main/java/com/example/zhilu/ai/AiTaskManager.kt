package com.example.zhilu.ai

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.datastore.UserPreferences
import com.example.zhilu.domain.ai.AiRefSnapshot
import com.example.zhilu.domain.ai.model.AiRef
import com.example.zhilu.domain.ai.model.AiRefKind
import com.example.zhilu.domain.ai.model.AiTask
import com.example.zhilu.domain.ai.model.AiTaskPhase
import com.example.zhilu.domain.ai.model.AiTaskState
import com.example.zhilu.domain.ai.repository.AiAssistantRepository
import com.example.zhilu.domain.ai.repository.AiAttempt
import com.example.zhilu.domain.model.AiMessage
import com.example.zhilu.domain.model.AiRole
import com.example.zhilu.domain.repository.AiConversationRepository
import com.example.zhilu.domain.repository.NoteRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    /** 本条消息「引用（回复）」的同会话消息 id；null = 不是引用消息。 */
    val quotedMessageId: Long? = null,
    val conversationId: Long? = null
)

/**
 * [AiTaskManager.submit] 的结果。
 *
 * 为什么不是直接返回 taskId：全局单任务互斥下，「提交」是一个**可能被拒的裁决**。
 * 调用方（输入栏）必须知道是「已受理」还是「请先停下当前回答」——否则拿到一个 id
 * 却永远等不到自己的流，用户只会以为消息发丢了。
 */
sealed interface AiSubmitResult {

    /** 已受理并入队，生成在后台 scope 执行。 */
    data class Accepted(val taskId: String) : AiSubmitResult

    /**
     * 被全局互斥拒绝：已有任务在跑。
     *
     * @param reason 可直接展示给用户的文案
     * @param running 正在跑的那条任务（同一时刻至多一条），UI 可据此定位/提示
     */
    data class Rejected(val reason: String, val running: AiTask?) : AiSubmitResult
}

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
    private val userPreferences: UserPreferences,
    private val taskHost: AiTaskHost
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(AiTaskState())
    val state: StateFlow<AiTaskState> = _state.asStateFlow()

    /** 正在跑的生成协程，按任务 id 索引——「停止」要能精确掐掉其中一条。 */
    private val jobs = ConcurrentHashMap<String, Job>()

    /**
     * 提交闸门。判定「有没有活跃任务」与「入队新任务」必须在同一把锁里完成。
     *
     * 不能靠 UI 侧 `isGenerating` 做门控：那是异步回填的派生状态，页面切换、会话切换
     * 造成的空窗期里它可能是 false，而任务其实在跑（真机 bug：任务还在生成却允许再发）。
     * 任务表只有 [submit] 一个「新增」写入口，锁在这里就闭死了。
     */
    private val submitLock = Any()

    init {
        // 任务是唯一事实源，前台服务与通知只是它的投影。统一在这里同步，
        // 免得每处 _state.update 都得记得手动通知一次（漏一处就会出现「进度不动了」）。
        scope.launch {
            _state.collect { snapshot -> taskHost.sync(snapshot) }
        }
    }

    /**
     * 提交任务（非挂起）：受理则立即入队并返回任务 id，生成在后台 scope 执行。
     *
     * **全局互斥的裁决点**（见 [submitLock]）：同一时刻只允许一条活跃任务，
     * 已有任务在跑时返回 [AiSubmitResult.Rejected] 且**不产生任何副作用**——
     * 调用方据此保留用户输入并提示，而不是让消息凭空消失。
     *
     * 已完成的任务会留在 [state] 表里（通知、笔记页的「刚生成完」都要读它），
     * 但它们不占锁：只有 [AiTask.isActive] 的才算「正在跑」。
     */
    fun submit(request: AiSubmitRequest): AiSubmitResult {
        val taskId = UUID.randomUUID().toString()
        synchronized(submitLock) {
            val running = _state.value.tasks.lastOrNull { it.isActive }
            if (running != null) {
                return AiSubmitResult.Rejected(
                    reason = "正在生成中，先停一下或等它回答完",
                    running = running
                )
            }
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
        }
        jobs[taskId] = scope.launch {
            try {
                run(taskId, request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 兜底：异常逃出去的话任务会永远停在「生成中」，那既锁住目标，
                // 也会让前台服务一直挂着。
                fail(taskId, e.message ?: "生成失败，请重试")
            } finally {
                jobs.remove(taskId)
            }
        }
        return AiSubmitResult.Accepted(taskId)
    }

    /**
     * 停止一个任务：掐掉协程并把它从任务表里移除。
     *
     * 用「移除」而不是标成「已取消」——任务表同时是目标占用锁与流式气泡的数据源，
     * 移出即可让两者一起收干净。半截回复留在会话里只会被当成结果看，不如不留。
     * 已完成的任务（协程早已结束、不在 [jobs] 里）不动，避免误删真结果。
     */
    fun cancel(taskId: String) {
        val job = jobs.remove(taskId) ?: return
        job.cancel()
        _state.update { s -> s.copy(tasks = s.tasks.filterNot { it.id == taskId }) }
    }

    private suspend fun run(taskId: String, request: AiSubmitRequest) {
        // 每次生成都重新解析当前服务：用户可能在生成开始前刚切了 AI，
        // 缓存下来会让"切了但没用上"这种问题极难查。
        val settings = userPreferences.aiSettings.first()
        val active = settings.resolveActive()
        if (active == null) {
            fail(taskId, "没有可用的 AI 服务，请先在「我的 → AI 配置」里添加")
            return
        }
        val attempt = AiAttempt(
            service = active,
            fallbacks = settings.fallbackChain(),
            fallbackEnabled = settings.fallbackOnFailure
        )
        val refsWithSnapshots = ensureRefSnapshots(request.refs)
        // contextText 只承载「本次附带图片」的 URI 说明；引用内容随 user 消息走
        // （refs 落库后由 AiUserMessageText 注入），不再往 system prompt 里拼。
        val contextText = resolveImageContext(request.images)

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
            fileText = request.fileContent,
            // 引用随消息落库（含内容快照）：历史回看时 chip 仍可展示，
            // 请求组装时也由它决定「这条消息引用了什么」。
            refs = refsWithSnapshots,
            // 引用回复：只存 id（同会话内自洽），正文由请求组装时按 id 现取——
            // 引用的消息本来就在历史里，抄一份正文只会多一份可能过期的副本。
            quotedMessageId = request.quotedMessageId
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
            attempt = attempt,
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
            onFallback = { serviceName ->
                // 只有一字未出时才会走到这里（仓库层保证），所以提示"换了个模型"是安全的，
                // 不会出现"回答说到一半换人"的错乱。
                update(taskId) { it.copy(toolName = null, phase = AiTaskPhase.RUNNING) }
                conversationRepository.addMessage(
                    AiMessage(
                        conversationId = conversationId,
                        role = AiRole.TOOL,
                        content = "已切换到 $serviceName",
                        toolName = serviceName
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

    /**
     * 保证每个引用都带内容快照。
     *
     * 正常路径什么都不做：快照在**引用那一刻**就由 `AiRefSnapshot` 冻结好了
     * （编辑器 id 是内存坐标，按 id 回查必然落空，见 `AiRef` 的类注释）。
     * 这里只兜防御路径：快照为空（老数据 / 异常入口）时按 id 回查一次补上；
     * 补不到就写明「不可用」——好过把一句空话丢给模型。
     */
    private suspend fun ensureRefSnapshots(refs: List<AiRef>): List<AiRef> =
        refs.map { ref ->
            if (ref.snapshot.isNotBlank()) ref else ref.copy(snapshot = resolveRefSnapshot(ref))
        }

    private suspend fun resolveRefSnapshot(ref: AiRef): String {
        val note = when (val result = noteRepository.getNoteById(ref.noteId)) {
            is RepositoryResult.Success -> result.data ?: return "（引用的内容已不可用）"
            is RepositoryResult.Error -> return "（读取引用内容失败：${result.message}）"
        }
        return when (ref.kind) {
            AiRefKind.NOTE -> AiRefSnapshot.forNote(note.title, note.contentBlocks)
            AiRefKind.CARD -> {
                val card = note.cards.firstOrNull { it.id == ref.cardId }
                if (card == null) "（引用的卡片已不可用）"
                else AiRefSnapshot.forCard(note.title, card.title, card.blocks)
            }
            AiRefKind.BLOCK -> {
                val block = note.contentBlocks.firstOrNull { it.id == ref.blockId }
                if (block == null) "（引用的块已不可用）"
                else AiRefSnapshot.forBlock(note.title, block)
            }
        }
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
