package com.example.zhilu.ui.assistant

import com.example.zhilu.domain.ai.model.AiRef
import com.example.zhilu.domain.ai.model.AiTask
import com.example.zhilu.domain.ai.model.AiTaskPhase
import com.example.zhilu.domain.model.AiConfig
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
    val aiConfig: AiConfig = AiConfig(),
    /** 当前会话的活跃生成任务（若无则为 null）。 */
    val activeTask: AiTask? = null,
    /** 助手页「引用笔记」选择器的候选（NOTE 级引用）。 */
    val refPickerNotes: List<AiRef> = emptyList(),
    val showRefPicker: Boolean = false,
    val error: String? = null,
    /** 一次性提示（非错误），如"已经在最新对话中"。弹完即消费。 */
    val notice: String? = null
) {
    /** 是否正在生成（由活跃任务派生，跨页面持久）。 */
    val isGenerating: Boolean
        get() = activeTask?.isActive == true

    /** 正在调用的工具名（TOOL_CALLING 阶段）。 */
    val toolStatus: String?
        get() = activeTask?.takeIf { it.phase == AiTaskPhase.TOOL_CALLING }?.toolName

    /** 流式累积文本（用于渲染末尾的合成气泡）。 */
    val streamingText: String?
        get() = activeTask?.takeIf { it.isActive && it.streamText.isNotBlank() }?.streamText
}
