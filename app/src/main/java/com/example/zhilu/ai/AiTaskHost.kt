package com.example.zhilu.ai

import com.example.zhilu.domain.ai.model.AiTaskState

/**
 * AI 任务在系统层的镜像。
 *
 * [AiTaskManager] 每次状态变化都调用一次 [sync]，由实现决定「要不要维持前台服务与通知」。
 * 抽出接口是为了让任务管理器（以及它的单测）不直接依赖 Android 平台 API。
 */
interface AiTaskHost {

    /**
     * 同步一次任务快照。实现需自行识别「哪些任务刚刚结束」——它比调用方更清楚
     * 通知该在什么时机发。
     */
    fun sync(state: AiTaskState)
}
