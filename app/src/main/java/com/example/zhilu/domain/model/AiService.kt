package com.example.zhilu.domain.model

import kotlinx.serialization.Serializable

/**
 * 一个 AI 服务（供应商 + 密钥 + 模型 + 参数）。
 *
 * ## 为什么不预设模型清单
 *
 * 早先的 `AiProvider` 每个供应商挂一串 `textModels` / `visionModels` 硬编码清单。
 * 那是错的：模型的增删改比 app 发版快得多，清单一旦过期，用户就只能填清单里有的，
 * 新的填不了、下线的还留着。**模型 ID 一律手填**，供应商预设只负责带出端点。
 *
 * ## 为什么只有一个 model
 *
 * 早先分了「文本模型」和「视觉模型」两个字段，因为默认了"识图要用专门的 VL 模型"。
 * 但现在主流大模型**一个就能同时处理文本和图片**，逼用户填两个字段只会让人以为
 * 必须配两个服务。所以只留 [model] 一个，发图时用它本身。
 *
 * ## 密钥存放
 *
 * [apiKey] 存在 DataStore 里（跟其他偏好同一个文件）。**没有额外加密** ——
 * 这一点必须诚实标注：它保护不了 root 设备或已导出的备份，与"本地加密存储"的宣传语
 * 有差距。真要做需要 EncryptedSharedPreferences 或 Tink，是独立的一件事。
 */
@Serializable
data class AiService(
    /** 稳定 id（UUID）。启动与切换都不靠数组下标，避免删除后错位。 */
    val id: String,
    /** 显示名称，如「我的 DeepSeek」。列表与切换条上显示的就是它。 */
    val name: String,
    /** 供应商显示名，如「DeepSeek」。只用于展示，不参与请求。 */
    val provider: String = "",
    /** OpenAI 兼容的 base URL，请求会拼上 `/chat/completions`。 */
    val endpoint: String,
    val apiKey: String = "",
    /** 模型 ID。**手填**，见类注释。 */
    val model: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    /** 系统提示词。留空则用应用内置的那份。 */
    val systemPrompt: String = "",
    /** 停用的服务不参与切换、也不会被自动选中（但保留配置）。 */
    val enabled: Boolean = true
) {
    /** 能发请求的最低要求。名字允许空（列表里会回退显示模型名）。 */
    val isConfigured: Boolean
        get() = endpoint.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    /** 界面上显示的名字：优先自定义名，其次模型 ID，最后占位。 */
    val displayName: String
        get() = name.ifBlank { model.ifBlank { "未命名" } }
}

/**
 * 供应商端点预设 —— **只是填表时的便利**，不是白名单。
 *
 * 全部按 OpenAI 兼容接口列出（这是当前事实标准）。选一个只会把 [provider] 与
 * [endpoint] 填进表单，两者之后都能改，所以自建网关 / 中转站一样能用。
 */
data class AiVendorPreset(
    val label: String,
    val endpoint: String
) {
    companion object {
        val presets: List<AiVendorPreset> = listOf(
            AiVendorPreset("DeepSeek", "https://api.deepseek.com/v1"),
            AiVendorPreset("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
            AiVendorPreset("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4"),
            AiVendorPreset("Moonshot", "https://api.moonshot.cn/v1"),
            AiVendorPreset("OpenAI", "https://api.openai.com/v1"),
            AiVendorPreset("OpenRouter", "https://openrouter.ai/api/v1"),
            AiVendorPreset("硅基流动", "https://api.siliconflow.cn/v1"),
            AiVendorPreset("Ollama（本地）", "http://localhost:11434/v1")
        )
    }
}

/**
 * 全部 AI 配置。
 *
 * [activeId] 是**当前使用**的服务；为 null 或指向一个已停用/已删除的服务时，
 * 由 [resolveActive] 回退到第一个启用的。
 */
@Serializable
data class AiSettings(
    val services: List<AiService> = emptyList(),
    val activeId: String? = null,
    /** 请求失败时是否换下一个可用服务重试一次。 */
    val fallbackOnFailure: Boolean = true
) {
    /** 启用的服务，按配置顺序。 */
    val enabledServices: List<AiService>
        get() = services.filter { it.enabled && it.isConfigured }

    val hasAny: Boolean get() = services.isNotEmpty()
    val hasUsable: Boolean get() = enabledServices.isNotEmpty()

    /**
     * 当前该用哪个服务。
     *
     * 三层回退，顺序固定：**显式选中的 → 第一个启用的 → null**。
     * 之所以要第二层：用户删掉或停用了当前服务时，`activeId` 会变成悬空引用；
     * 若不回退，界面会显示"当前使用：某某"而实际发不出请求。
     */
    fun resolveActive(): AiService? =
        services.firstOrNull { it.id == activeId && it.enabled && it.isConfigured }
            ?: enabledServices.firstOrNull()

    /**
     * 失败回退的候选顺序：当前服务之后的其他启用服务（不含自己），
     * 绕回队首，保证每个服务最多试一次。
     */
    fun fallbackChain(): List<AiService> {
        if (!fallbackOnFailure) return emptyList()
        val active = resolveActive() ?: return emptyList()
        val others = enabledServices.filter { it.id != active.id }
        if (others.isEmpty()) return emptyList()
        val activeIndex = enabledServices.indexOfFirst { it.id == active.id }
        val tail = enabledServices.drop(activeIndex + 1).filter { it.id != active.id }
        val head = enabledServices.take(activeIndex).filter { it.id != active.id }
        return tail + head
    }

    companion object {
        val EMPTY = AiSettings()
    }
}
