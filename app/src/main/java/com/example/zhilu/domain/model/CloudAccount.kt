package com.example.zhilu.domain.model

import kotlinx.serialization.Serializable

/**
 * 云端账号。
 *
 * 字段名与云端 JSON **逐字一致**，所以它既是领域模型、也是反序列化目标。
 * 唯一被改写的是 [avatarUrl]：服务端只返回相对路径（`/avatars/x.png`），
 * 由仓库层拼上当前服务地址 —— 存进 DataStore 的必须是可直接加载的绝对地址。
 */
@Serializable
data class CloudUser(
    val id: Long = 0,
    val email: String = "",
    val username: String = "",
    val avatarUrl: String? = null,
    val emailVerified: Boolean = false,
    val createdAt: Long = 0
)

/** 云端那份 `.dtk` 的元信息。 */
@Serializable
data class CloudBackupInfo(
    val fileName: String = "",
    val size: Long = 0,
    val updatedAt: Long = 0
)

/**
 * 服务端能力探测结果。
 *
 * 界面靠它决定要不要显示「获取验证码」：服务端没配 SMTP 时那个按钮永远发不出信，
 * 灰着比点完报错好。
 */
data class CloudHealth(
    val version: String = "",
    val mailConfigured: Boolean = false,
    val registrationEnabled: Boolean = true,
    val requireEmailVerification: Boolean = false
)

/** 验证码用途。`wire` 是接口字段值，别改名。 */
enum class CloudCodePurpose(val wire: String) {
    REGISTER("register"),
    RESET("reset")
}

data class SendCodeInfo(
    val cooldownSeconds: Int = 60,
    val ttlMinutes: Int = 10
)

/**
 * 云端连接配置。整体一个 JSON 存在 DataStore 里（与 `ai_settings` 同样的理由：
 * 字段会变，拆成多个 key 每加一个字段都要动迁移）。
 *
 * [serverUrl] 为空 = **不使用云端**，这是默认状态：整个功能对本地用户完全隐身。
 */
@Serializable
data class CloudSettings(
    val serverUrl: String = "",
    val token: String = "",
    val user: CloudUser? = null
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank()

    val isSignedIn: Boolean get() = token.isNotBlank()

    companion object {
        val EMPTY = CloudSettings()
    }
}
