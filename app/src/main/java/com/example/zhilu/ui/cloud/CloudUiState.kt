package com.example.zhilu.ui.cloud

import com.example.zhilu.domain.model.CloudBackupInfo
import com.example.zhilu.domain.model.CloudHealth
import com.example.zhilu.domain.model.CloudSettings
import com.example.zhilu.domain.model.CloudUser

/** 账号区的三种形态。找回密码也在这个页面里，不另开一页。 */
enum class CloudAuthMode { LOGIN, REGISTER, RESET }

/**
 * 云端服务页的状态。
 *
 * [serverUrlDraft] 与已保存的地址分开：用户改了一半的地址不该影响正在使用的连接，
 * 只有点了「保存」才生效（并顺带清掉旧的登录态，见 `CloudRepository.saveServerUrl`）。
 */
data class CloudUiState(
    val settings: CloudSettings = CloudSettings.EMPTY,
    val health: CloudHealth? = null,
    val isWorking: Boolean = false,
    val message: String? = null,
    val error: String? = null,

    val serverUrlDraft: String = "",
    val isEditingServer: Boolean = false,

    val authMode: CloudAuthMode = CloudAuthMode.LOGIN,
    val email: String = "",
    val password: String = "",
    val code: String = "",
    val username: String = "",
    /** 「获取验证码」的冷却倒计时（秒），> 0 时按钮不可点 */
    val codeCountdown: Int = 0,
    /** 验证码有效期（分钟），来自服务端；用于提示文案 */
    val codeTtlMinutes: Int = 10,

    val backupInfo: CloudBackupInfo? = null,
    val isLoadingBackup: Boolean = false
) {
    val user: CloudUser? get() = settings.user
    val isConfigured: Boolean get() = settings.isConfigured
    val isSignedIn: Boolean get() = settings.isSignedIn

    /** 注册要填验证码吗？以服务端探测结果为准（它没配 SMTP 就不该让用户傻等邮件）。 */
    val needsCodeForRegister: Boolean get() = health?.requireEmailVerification == true

    /** 当前是否需要验证码（注册 / 找回密码都要）。 */
    val needsCode: Boolean
        get() = authMode == CloudAuthMode.RESET || (authMode == CloudAuthMode.REGISTER && needsCodeForRegister)

    val canSendCode: Boolean
        get() = health?.mailConfigured == true &&
            codeCountdown == 0 &&
            email.isNotBlank() &&
            !isWorking

    /** 提交按钮能不能点：只做"明显填错了"的本地拦截，格式细节交给服务端。 */
    val canSubmit: Boolean
        get() = !isWorking &&
            email.isNotBlank() &&
            password.isNotBlank() &&
            password.length >= MIN_PASSWORD &&
            (!needsCode || code.length == 6) &&
            (authMode != CloudAuthMode.REGISTER || health?.registrationEnabled != false)

    companion object {
        const val MIN_PASSWORD = 8
    }
}
