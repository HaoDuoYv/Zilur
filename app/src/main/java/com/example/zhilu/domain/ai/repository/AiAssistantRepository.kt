package com.example.zhilu.domain.ai.repository

import com.example.zhilu.domain.model.AiMessage
import com.example.zhilu.domain.model.AiService
import com.example.zhilu.domain.model.AiSettings

/** 一次生成用的服务与它背后的完整配置（回退时需要知道还有哪些可选）。 */
data class AiAttempt(
    val service: AiService,
    /** 失败后要依次尝试的服务；为空表示不回退。 */
    val fallbacks: List<AiService>,
    /** 全局是否允许失败回退。 */
    val fallbackEnabled: Boolean
)

interface AiAssistantRepository {
    /**
     * 生成一条 AI 回复。
     *
     * @param attempt 当前服务 + 回退链（由 [AiSettings.resolveActive] / [AiSettings.fallbackChain] 给出）
     * @param history 对话历史（USER / ASSISTANT 交替，不含 system prompt）
     * @param onDelta 流式增量回调，每次回调追加一段文本
     * @param onToolEvent 工具调用事件回调，参数为工具名；每开始执行一个工具回调一次
     * @param onFallback 切换到备用服务时回调，参数是新的服务名（界面据此提示用户）。
     *   是 `suspend` 的：调用方要在切服务前把"已切换"这条消息落库，不能只弹个提示。
     * @param contextText 附加到 system prompt 的引用上下文（被引用笔记/卡片/块的文本）
     * @return 完整回复文本；失败时返回 Result.failure
     */
    suspend fun generateReply(
        attempt: AiAttempt,
        history: List<AiMessage>,
        onDelta: (String) -> Unit,
        onToolEvent: suspend (String) -> Unit = {},
        onFallback: suspend (String) -> Unit = {},
        contextText: String = ""
    ): Result<String>
}
