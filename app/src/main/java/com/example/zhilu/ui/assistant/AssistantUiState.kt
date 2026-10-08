package com.example.zhilu.ui.assistant

import com.example.zhilu.domain.ai.model.AiRef
import com.example.zhilu.domain.ai.model.AiTask
import com.example.zhilu.domain.ai.model.AiTaskPhase
import com.example.zhilu.domain.model.AiService
import com.example.zhilu.domain.model.AiSettings
import com.example.zhilu.domain.model.AiConversation
import com.example.zhilu.domain.model.AiMessage

/** 待发送的文本附件（txt / md）。 */
data class AttachedFile(
    val name: String,
    val content: String
)

data class AssistantUiState(
    val conversations: List<AiConversation> = emptyList(),
    val currentConversationId: Long? = null,
    val messages: List<AiMessage> = emptyList(),
    val inputText: String = "",
    val attachedImages: List<String> = emptyList(),
    val attachedFile: AttachedFile? = null,
    val attachedRefs: List<AiRef> = emptyList(),
    /**
     * 待发送的「引用消息」（长按气泡 → 引用）。
     *
     * 存的是整条消息而不只是 id：输入条要显示摘要、气泡要显示引用块，发送时也只用它的 id
     * （落库为 `quotedMessageId`，AI 侧由 `AiUserMessageText` 注入【引用的消息】）。
     */
    val quotedMessage: AiMessage? = null,
    /** 全部 AI 配置。当前用哪个由 resolveActive() 决定，不单独存字段。 */
    val aiSettings: AiSettings = AiSettings.EMPTY,
    /**
     * 全局正在跑的那条任务（互斥锁的 UI 投影，同一时刻至多一条）。
     *
     * 它**不按会话过滤**：任务可能属于别的会话（生成中切了对话、或从笔记页发起），
     * 但锁是全局的——输入栏必须继续显示「停止」，因为此刻发送一定会被拒。
     * 要在哪个会话里渲染流式气泡，看派生的 [activeTask]。
     */
    val runningTask: AiTask? = null,
    /** 助手页「引用笔记」选择器的候选（NOTE 级引用）。 */
    val refPickerNotes: List<AiRef> = emptyList(),
    val showRefPicker: Boolean = false,
    val error: String? = null,
    /** 一次性提示（非错误），如"已经在最新对话中"。弹完即消费。 */
    val notice: String? = null
) {
    /**
     * 当前会话的活跃任务——只有它才该在本会话渲染流式气泡 / 工具状态。
     *
     * 由 [runningTask] 派生而不是另存一份字段：另存的那份只会在任务状态变化时被刷新，
     * 切换会话时不会重算，会把上一条对话的流式气泡串进新会话里
     * （旧实现为此在切会话时手动清 `activeTask`，又顺手清掉了「正在生成」的互斥信号）。
     */
    val activeTask: AiTask?
        get() = runningTask?.takeIf { it.conversationId == currentConversationId }

    /**
     * 是否存在活跃生成（**全局**）——输入栏据此显示「停止」而不是「发送」。
     */
    val isGenerating: Boolean
        get() = runningTask?.isActive == true

    /**
     * 当前会话是否正在生成（**本会话**）——流式气泡 / 工具高亮 / 滚动锚点看它。
     */
    val isStreamingVisible: Boolean
        get() = activeTask?.isActive == true

    /** 正在调用的工具名（TOOL_CALLING 阶段）。 */
    val toolStatus: String?
        get() = activeTask?.takeIf { it.phase == AiTaskPhase.TOOL_CALLING }?.toolName

    /** 流式累积文本（用于渲染末尾的合成气泡）。 */
    val streamingText: String?
        get() = activeTask?.takeIf { it.isActive && it.streamText.isNotBlank() }?.streamText
}
