package com.example.zhilu.domain.ai.repository

import com.example.zhilu.domain.model.AiConfig
import com.example.zhilu.domain.model.AiMessage

interface AiAssistantRepository {
    /**
     * 生成一条 AI 回复。
     *
     * @param config AI 配置（端点 / Key / 模型）
     * @param history 对话历史（USER / ASSISTANT 交替，不含 system prompt）
     * @param onDelta 流式增量回调，每次回调追加一段文本
     * @param onToolEvent 工具调用事件回调，参数为工具名；每开始执行一个工具回调一次
     * @param contextText 附加到 system prompt 的引用上下文（被引用笔记/卡片/块的文本）
     * @return 完整回复文本；失败时返回 Result.failure
     */
    suspend fun generateReply(
        config: AiConfig,
        history: List<AiMessage>,
        onDelta: (String) -> Unit,
        onToolEvent: suspend (String) -> Unit = {},
        contextText: String = ""
    ): Result<String>
}
