package com.example.zhilu.domain.model

/**
 * OpenAI 兼容供应商预设。
 * 预设供应商端点固定、模型可下拉选择；[CUSTOM_ID] 为自定义供应商，端点与模型均手动填写。
 */
data class AiProvider(
    val id: String,
    val label: String,
    val endpoint: String,
    val textModels: List<String> = emptyList(),
    val visionModels: List<String> = emptyList()
) {
    companion object {
        const val CUSTOM_ID = "custom"
        const val DEFAULT_ID = "deepseek"

        val presets: List<AiProvider> = listOf(
            AiProvider(
                id = "deepseek",
                label = "DeepSeek",
                endpoint = "https://api.deepseek.com/v1",
                textModels = listOf("deepseek-chat", "deepseek-reasoner")
            ),
            AiProvider(
                id = "qwen",
                label = "通义千问",
                endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1",
                textModels = listOf("qwen-plus", "qwen-turbo", "qwen-max", "qwen-flash"),
                visionModels = listOf("qwen-vl-plus", "qwen-vl-max")
            ),
            AiProvider(
                id = "zhipu",
                label = "智谱",
                endpoint = "https://open.bigmodel.cn/api/paas/v4",
                textModels = listOf("glm-4-plus", "glm-4-air", "glm-4-flash"),
                visionModels = listOf("glm-4v-plus", "glm-4v-flash")
            ),
            AiProvider(
                id = "moonshot",
                label = "Moonshot",
                endpoint = "https://api.moonshot.cn/v1",
                textModels = listOf("kimi-latest", "moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k"),
                visionModels = listOf("moonshot-v1-8k-vision-preview", "moonshot-v1-32k-vision-preview")
            ),
            AiProvider(
                id = "openai",
                label = "OpenAI",
                endpoint = "https://api.openai.com/v1",
                textModels = listOf("gpt-4o", "gpt-4o-mini", "gpt-4.1-mini"),
                visionModels = listOf("gpt-4o", "gpt-4o-mini")
            ),
            AiProvider(
                id = CUSTOM_ID,
                label = "自定义",
                endpoint = ""
            )
        )

        fun byId(id: String): AiProvider = presets.firstOrNull { it.id == id } ?: presets.last()
    }
}
