package com.example.zhilu.domain.ai.model

/**
 * 暴露给 LLM 的工具定义（Function Calling）。
 * [parametersJson] 为 JSON Schema 字符串，data 层负责转换为协议 DTO。
 * 与协议 DTO 解耦，保持 domain 不依赖 data。
 */
data class AiToolDefinition(
    val name: String,
    val description: String,
    val parametersJson: String
)
