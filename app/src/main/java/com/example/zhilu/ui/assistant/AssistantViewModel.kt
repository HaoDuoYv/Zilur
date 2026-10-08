package com.example.zhilu.ui.assistant

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhilu.ai.AiPromptHandoff
import com.example.zhilu.ai.AiRefManager
import com.example.zhilu.ai.AiSubmitRequest
import com.example.zhilu.ai.AiSubmitResult
import com.example.zhilu.ai.AiTaskManager
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.datastore.UserPreferences
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.ai.AiRefSnapshot
import com.example.zhilu.domain.ai.model.AiRef
import com.example.zhilu.domain.ai.model.AiRefKind
import com.example.zhilu.domain.ai.model.AiTaskPhase
import com.example.zhilu.domain.ai.model.distinctByTarget
import com.example.zhilu.domain.model.AiMessage
import com.example.zhilu.domain.repository.AiConversationRepository
import com.example.zhilu.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 助手页 ViewModel：只负责「提交任务 + 观察状态 + 渲染」，不再承载生成协程。
 *
 * 生成生命周期由进程级 [AiTaskManager] 管理，切换页面 / 离开助手页都不会中断。
 * - 消息列表由 Room Flow 驱动（[AiConversationRepository.getMessages]），作为唯一来源；
 * - 流式文本 / 工具状态 / 生成中态由 [AiTaskManager.state] 派生；
 * - 引用由 [AiRefManager] 跨页交接。
 * - 当前会话与输入草稿由 [SavedStateHandle] 托管：进程被系统回收后回到助手页，
 *   输入框和所在对话都还在（任务本身随进程一起没，那部分无能为力）。
 */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val conversationRepository: AiConversationRepository,
    private val userPreferences: UserPreferences,
    private val mediaFileManager: MediaFileManager,
    private val taskManager: AiTaskManager,
    private val refManager: AiRefManager,
    private val promptHandoff: AiPromptHandoff,
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AssistantUiState(
            currentConversationId = savedStateHandle.get<Long>(KEY_CONVERSATION_ID),
            inputText = savedStateHandle.get<String>(KEY_INPUT_TEXT).orEmpty()
        )
    )
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var lastSubmittedText: String? = null
    private val surfacedErrorTaskIds = mutableSetOf<String>()

    /**
     * 本 VM 提交、尚未「认领会话」的任务 id（见 [observeTasks] 的新会话回流）。
     *
     * 只认领**自己**提交的那条：全局互斥下任务表里可能有别人的任务，
     * 若不加这道门，一个刚点开空白对话的用户会被那条任务硬拽到别的会话去。
     * 用户主动切会话 / 新建对话（表达「我不想去那儿」）时清掉。
     */
    private var pendingOwnTaskId: String? = null

    /**
     * 一键切换当前 AI。
     *
     * 只改 `activeId` 并落库 —— **不影响正在进行的生成**：`AiTaskManager` 是每次
     * 生成开始时才解析当前服务，中途换不会把两段回答拼在一起。
     */
    fun switchActiveService(serviceId: String) {
        viewModelScope.launch {
            val settings = _uiState.value.aiSettings
            if (settings.activeId == serviceId) return@launch
            userPreferences.setAiSettings(settings.copy(activeId = serviceId))
        }
    }

    init {
        viewModelScope.launch {
            userPreferences.aiSettings.collect { settings ->
                _uiState.update { it.copy(aiSettings = settings) }
            }
        }
        viewModelScope.launch {
            conversationRepository.getConversations().collect { result ->
                when (result) {
                    is RepositoryResult.Success -> _uiState.update { it.copy(conversations = result.data) }
                    is RepositoryResult.Error -> Unit
                }
            }
        }
        observeMessages()
        observeTasks()
        observePendingRefs()
        observePromptHandoff()
        persistDraft()
    }

    /**
     * 吸附跨页交来的「AI 创建」开场白（见 [AiPromptHandoff]）。
     *
     * 与引用交接（[observePendingRefs]）同一套路：先清空再落输入框，
     * 否则进程内任何一次重组/重进页面都会把同一句话再填一遍。
     */
    private fun observePromptHandoff() {
        viewModelScope.launch {
            promptHandoff.prompt.collect { prompt ->
                if (prompt.isNotBlank()) {
                    promptHandoff.consume()
                    _uiState.update { it.copy(inputText = prompt) }
                }
            }
        }
    }

    /**
     * 当前会话 + 输入草稿持久化。
     *
     * 在这里集中镜像而不是在每个写入点各写一遍：`sendMessage` 清空、失败时回填、
     * 采纳新会话……写入点有六七处，漏一处就是一类「回来发现丢东西」的偶发 bug。
     */
    private fun persistDraft() {
        viewModelScope.launch {
            _uiState
                .map { it.currentConversationId to it.inputText }
                .distinctUntilChanged()
                .collect { (conversationId, inputText) ->
                    savedStateHandle[KEY_CONVERSATION_ID] = conversationId
                    savedStateHandle[KEY_INPUT_TEXT] = inputText
                }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            _uiState.map { it.currentConversationId }
                .distinctUntilChanged()
                .collectLatest { id ->
                    if (id == null) {
                        _uiState.update { it.copy(messages = emptyList()) }
                        return@collectLatest
                    }
                    conversationRepository.getMessages(id).collect { result ->
                        when (result) {
                            is RepositoryResult.Success -> _uiState.update { it.copy(messages = result.data) }
                            is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
                        }
                    }
                }
        }
    }

    private fun observeTasks() {
        viewModelScope.launch {
            taskManager.state.collect { taskState ->
                // 全局单任务：表里至多一条活跃任务，直接作为输入栏「停止」的依据。
                val running = taskState.activeTasks.lastOrNull()
                val ownTask = pendingOwnTaskId?.let { id ->
                    taskState.activeTasks.firstOrNull { it.id == id }
                }
                val currentId = _uiState.value.currentConversationId
                // 新建会话回流：任务先以 conversationId=0 入队，解析出会话后才把视图带过去。
                // 只认自己提交的那条，且只在「还没有会话」时认领。
                val adoptId = ownTask
                    ?.takeIf { currentId == null && it.conversationId != 0L }
                    ?.conversationId
                val effectiveId = adoptId ?: currentId
                val failed = taskState.tasks.lastOrNull {
                    it.phase == AiTaskPhase.FAILED &&
                        it.error != null &&
                        it.id !in surfacedErrorTaskIds &&
                        (it.conversationId == effectiveId ||
                            (effectiveId == null && it.conversationId == 0L))
                }
                _uiState.update { s ->
                    var next = s.copy(runningTask = running)
                    if (adoptId != null) next = next.copy(currentConversationId = adoptId)
                    if (failed != null) {
                        surfacedErrorTaskIds += failed.id
                        next = next.copy(error = failed.error, inputText = lastSubmittedText.orEmpty())
                    }
                    next
                }
                // 认领完成 / 任务已离场 → 标记作废，避免用户切走后再被拽回去。
                if (adoptId != null || (pendingOwnTaskId != null && ownTask == null)) {
                    pendingOwnTaskId = null
                }
            }
        }
    }

    private fun observePendingRefs() {
        viewModelScope.launch {
            refManager.pendingRefs.collect { refs ->
                if (refs.isNotEmpty()) {
                    _uiState.update {
                        it.copy(attachedRefs = (it.attachedRefs + refs).distinctByTarget())
                    }
                    refManager.clearPending()
                }
            }
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /**
     * 附加图片：先把图复制到应用内部存储（file:// URI 持久有效），
     * 历史会话回看时缩略图不再因临时授权失效而无法加载。
     * 失败必须给出提示——静默失败会让用户以为「点了添加图片没反应」。
     */
    fun attachImages(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = withContext(Dispatchers.IO) {
                uris.map { uriString ->
                    uriString to mediaFileManager.importUriToInternal(Uri.parse(uriString)).getOrNull()
                }
            }
            val persisted = imported.mapNotNull { it.second }
            val failedCount = imported.size - persisted.size
            _uiState.update { state ->
                state.copy(
                    attachedImages = state.attachedImages + persisted,
                    error = when {
                        failedCount == 0 -> state.error
                        persisted.isEmpty() -> "图片添加失败，请换一张图片重试"
                        else -> "有 $failedCount 张图片添加失败"
                    }
                )
            }
        }
    }

    fun removeImage(index: Int) {
        _uiState.update { state ->
            if (index !in state.attachedImages.indices) state
            else state.copy(attachedImages = state.attachedImages.toMutableList().apply { removeAt(index) })
        }
    }

    fun attachFile(name: String, content: String) {
        _uiState.update { it.copy(attachedFile = AttachedFile(name = name, content = content)) }
    }

    fun removeFile() {
        _uiState.update { it.copy(attachedFile = null) }
    }

    fun removeRef(index: Int) {
        _uiState.update { state ->
            if (index !in state.attachedRefs.indices) state
            else state.copy(attachedRefs = state.attachedRefs.toMutableList().apply { removeAt(index) })
        }
    }

    /**
     * 引用（回复）某条消息：输入条上方会出现一条引用条，发送时随 user 消息落库。
     *
     * 与「引用知识内容」（[attachedRefs]）是两回事：那个引的是笔记/卡片/块，这个引的是
     * 对话里的某条消息（IM 的"引用回复"形态），两者可以同时带。
     */
    fun quoteMessage(message: AiMessage) {
        _uiState.update { it.copy(quotedMessage = message) }
    }

    fun clearQuotedMessage() {
        _uiState.update { it.copy(quotedMessage = null) }
    }

    fun openRefPicker() {
        viewModelScope.launch {
            when (val result = noteRepository.getAllNotes().first()) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(
                        refPickerNotes = result.data.map { note ->
                            AiRef(kind = AiRefKind.NOTE, noteId = note.id, title = note.title)
                        },
                        showRefPicker = true
                    )
                }
                is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    fun dismissRefPicker() {
        _uiState.update { it.copy(showRefPicker = false) }
    }

    /**
     * 助手页「引用笔记」选择器选中：补内容快照后并入待发送引用。
     *
     * picker 打开时只装了元数据（不对全库做格式化），快照在这里**按需**生成 ——
     * 引用内容必须在引用这一刻冻结（见 `AiRef` 类注释）；查不到时保留空快照，
     * 请求期还有一次按 id 回查的兜底（`AiTaskManager.ensureRefSnapshots`）。
     */
    fun addRef(ref: AiRef) {
        _uiState.update { it.copy(showRefPicker = false) }
        viewModelScope.launch {
            val snapshot = when (val result = noteRepository.getNoteById(ref.noteId)) {
                is RepositoryResult.Success -> result.data
                    ?.let { AiRefSnapshot.forNote(it.title, it.contentBlocks) }
                    .orEmpty()
                is RepositoryResult.Error -> ""
            }
            val enriched = ref.copy(snapshot = snapshot)
            _uiState.update {
                it.copy(attachedRefs = (it.attachedRefs + enriched).distinctByTarget())
            }
        }
    }

    /**
     * 新建对话。
     *
     * **已经在一条最新的空对话里时不再重复新建**，改为回一句提示 —— 否则点了完全没反应，
     * 用户会以为按钮坏了（真机反馈）。判据是"没有落库的会话 id + 没有任何消息"，
     * 也就是当前这块就是刚开出来的空白对话。
     */
    fun newConversation() {
        if (isOnFreshConversation()) {
            _uiState.update { it.copy(notice = "已经在最新对话中") }
            return
        }
        // 用户明确表达「我要开一页新的」：作废回流标记，别把视图拽回正在生成的那条会话。
        // 正在生成的任务本身**不清**（互斥与停止按钮都靠它），输入栏会继续显示「停止」。
        pendingOwnTaskId = null
        _uiState.update {
            it.copy(
                currentConversationId = null,
                messages = emptyList(),
                inputText = "",
                attachedImages = emptyList(),
                attachedFile = null,
                attachedRefs = emptyList(),
                quotedMessage = null,
                error = null
            )
        }
    }

    /** 当前是不是"刚开出来的空对话"。 */
    private fun isOnFreshConversation(): Boolean = _uiState.value.let {
        it.currentConversationId == null &&
            it.messages.isEmpty() &&
            !it.isStreamingVisible
    }

    fun consumeNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    fun selectConversation(id: Long) {
        if (_uiState.value.currentConversationId == id) return
        // 同 newConversation：作废回流标记；正在生成的任务保留（全局互斥 + 停止按钮靠它）。
        pendingOwnTaskId = null
        _uiState.update {
            it.copy(
                currentConversationId = id,
                inputText = "",
                attachedImages = emptyList(),
                attachedFile = null,
                attachedRefs = emptyList(),
                // 引用的消息属于原会话：换会话后引用条必须跟着消失（引用跨会话没有意义）。
                quotedMessage = null
            )
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            conversationRepository.deleteConversation(id)
            if (_uiState.value.currentConversationId == id) {
                newConversation()
            }
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        val images = state.attachedImages
        val file = state.attachedFile
        val refs = state.attachedRefs
        if (text.isEmpty() && images.isEmpty() && file == null && refs.isEmpty()) return

        // 判断的是「有没有可用的服务」，而不是某一份配置填全没有 ——
        // 服务列表为空、或全都停用/没填全时才算没配置。
        if (!state.aiSettings.hasUsable) {
            _uiState.update { it.copy(error = "请先在「我的 → AI 配置」里添加一个 AI 服务") }
            return
        }

        // 互斥的裁决在任务管理器里（那里才是任务表的唯一写入口），这里只消费结果：
        // 被拒时**一根手指都不动输入框** —— 用户刚打好的稿子、图片、引用必须原样留着，
        // 否则「发送失败」就变成了「东西没了」。
        when (val result = taskManager.submit(
            AiSubmitRequest(
                text = text,
                images = images,
                fileName = file?.name,
                fileContent = file?.content,
                refs = refs,
                quotedMessageId = state.quotedMessage?.id,
                conversationId = state.currentConversationId
            )
        )) {
            is AiSubmitResult.Accepted -> {
                lastSubmittedText = text
                pendingOwnTaskId = result.taskId
                _uiState.update {
                    it.copy(
                        inputText = "",
                        attachedImages = emptyList(),
                        attachedFile = null,
                        attachedRefs = emptyList(),
                        quotedMessage = null,
                        error = null
                    )
                }
            }
            is AiSubmitResult.Rejected -> {
                _uiState.update { it.copy(notice = result.reason) }
            }
        }
    }

    /**
     * 停止当前生成任务。输入区在生成中会把「发送」换成「停止」——
     * 停止的是**全局那一条**（任何会话生成中，输入栏显示的都是它）：
     * 全局互斥下，不把它停掉，这个页面就一直发不出消息。
     */
    fun stopGeneration() {
        val taskId = _uiState.value.runningTask?.id ?: return
        taskManager.cancel(taskId)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

/** [SavedStateHandle] 键：当前会话 id（Long?）与输入草稿。 */
private const val KEY_CONVERSATION_ID = "assistantConversationId"
private const val KEY_INPUT_TEXT = "assistantInputText"
