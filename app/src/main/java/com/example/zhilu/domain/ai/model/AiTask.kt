package com.example.zhilu.domain.ai.model

/**
 * AI 生成任务的阶段。
 *
 * [isActive] 覆盖 QUEUED / RUNNING / TOOL_CALLING / STREAMING，均表示「目标处于生成占用中」。
 */
enum class AiTaskPhase { QUEUED, RUNNING, TOOL_CALLING, STREAMING, SUCCEEDED, FAILED }

/**
 * 一次 AI 生成任务的快照，由 [AiTaskManager] 以 StateFlow 对外广播。
 */
data class AiTask(
    val id: String,
    val conversationId: Long = 0L,
    val phase: AiTaskPhase = AiTaskPhase.QUEUED,
    val toolName: String? = null,
    val streamText: String = "",
    val refs: List<AiRef> = emptyList(),
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isActive: Boolean
        get() = phase == AiTaskPhase.QUEUED ||
            phase == AiTaskPhase.RUNNING ||
            phase == AiTaskPhase.TOOL_CALLING ||
            phase == AiTaskPhase.STREAMING
}

/**
 * 全局 AI 任务状态：任务列表 + 派生出的「被占用目标集合」。
 */
data class AiTaskState(
    val tasks: List<AiTask> = emptyList()
) {
    val activeTasks: List<AiTask>
        get() = tasks.filter { it.isActive }

    /** 当前被生成占用的目标（去重），UI 据此渲染「生成中」并禁点。 */
    val lockedRefs: Set<AiRef>
        get() = activeTasks.flatMap { it.refs }.toSet()
}
