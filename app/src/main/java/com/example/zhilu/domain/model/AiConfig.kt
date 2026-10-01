package com.example.zhilu.domain.model

data class AiConfig(
    val provider: String = AiProvider.DEFAULT_ID,
    val endpoint: String = "",
    val apiKey: String = "",
    val model: String = "",
    val visionModel: String = ""
) {
    val isConfigured: Boolean
        get() = endpoint.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}
