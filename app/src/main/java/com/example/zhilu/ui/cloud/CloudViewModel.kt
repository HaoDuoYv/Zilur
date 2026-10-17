package com.example.zhilu.ui.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.CloudCodePurpose
import com.example.zhilu.domain.repository.CloudRepository
import com.example.zhilu.domain.usecase.ExportBackupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 云端服务页。
 *
 * 三段：**服务地址**（可填自己部署的地址）、**账号**（登录 / 注册 / 找回密码 + 用户中心）、
 * **云端备份**（手动上传 / 删除；恢复入口在「＋ → 云端恢复」，因为恢复本质上是导入）。
 *
 * 云端是可选功能：这里所有失败都收敛成一句可读的提示，绝不让界面崩掉。
 */
@HiltViewModel
class CloudViewModel @Inject constructor(
    private val cloudRepository: CloudRepository,
    private val exportBackup: ExportBackupUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloudUiState())
    val uiState: StateFlow<CloudUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        viewModelScope.launch {
            cloudRepository.settings.collect { settings ->
                _uiState.update { state ->
                    state.copy(
                        settings = settings,
                        // 第一次拿到已保存的地址时把它填进输入框；
                        // 之后不覆盖用户正在编辑的草稿
                        serverUrlDraft = if (state.serverUrlDraft.isBlank()) {
                            settings.serverUrl
                        } else {
                            state.serverUrlDraft
                        }
                    )
                }
            }
        }
        // 已配置就直接探一次能力（决定要不要显示"获取验证码"、注册是否开放）
        viewModelScope.launch {
            val current = _uiState.value.settings
            if (current.isConfigured) loadHealth(current.serverUrl)
        }
        // 只在"登录态翻转"时拉备份元信息：否则改个用户名也会触发一次网络请求
        viewModelScope.launch {
            cloudRepository.settings
                .map { it.isSignedIn }
                .distinctUntilChanged()
                .collect { signedIn ->
                    if (signedIn) {
                        refreshBackupInternal()
                    } else {
                        _uiState.update { it.copy(backupInfo = null) }
                    }
                }
        }
    }

    // ── 服务地址 ──────────────────────────────────────────────────────

    fun startEditingServer() {
        _uiState.update { it.copy(isEditingServer = true, serverUrlDraft = it.settings.serverUrl) }
    }

    fun onServerUrlChange(value: String) {
        _uiState.update { it.copy(serverUrlDraft = value) }
    }

    fun cancelEditingServer() {
        _uiState.update { it.copy(isEditingServer = false, serverUrlDraft = it.settings.serverUrl) }
    }

    /** 只测连通性，不保存。用户想先确认地址对不对再存。 */
    fun testConnection() {
        val draft = _uiState.value.serverUrlDraft
        if (draft.isBlank()) {
            _uiState.update { it.copy(error = "请先填写服务地址") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            when (val result = cloudRepository.testConnection(draft)) {
                is RepositoryResult.Success -> {
                    val health = result.data
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            health = health,
                            message = "连接成功（服务端 v${health.version}）"
                        )
                    }
                }

                is RepositoryResult.Error ->
                    _uiState.update { it.copy(isWorking = false, error = result.message) }
            }
        }
    }

    fun saveServerUrl() {
        val draft = _uiState.value.serverUrlDraft
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            when (val result = cloudRepository.saveServerUrl(draft)) {
                is RepositoryResult.Success -> {
                    val normalized = result.data
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            isEditingServer = false,
                            serverUrlDraft = normalized,
                            message = if (normalized.isBlank()) {
                                "已关闭云端服务，笔记仍完全可用"
                            } else {
                                "已保存服务地址"
                            }
                        )
                    }
                    if (normalized.isNotBlank()) loadHealth(normalized)
                }

                is RepositoryResult.Error ->
                    _uiState.update { it.copy(isWorking = false, error = result.message) }
            }
        }
    }

    private suspend fun loadHealth(base: String) {
        when (val result = cloudRepository.testConnection(base)) {
            is RepositoryResult.Success -> _uiState.update { it.copy(health = result.data) }
            // 探测失败不打扰用户：真正的动作（登录/备份）会给出更具体的报错
            is RepositoryResult.Error -> _uiState.update { it.copy(health = null) }
        }
    }

    // ── 账号表单 ──────────────────────────────────────────────────────

    fun switchAuthMode(mode: CloudAuthMode) {
        _uiState.update {
            it.copy(authMode = mode, error = null, code = "")
        }
    }

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value.trim()) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value) }

    fun onCodeChange(value: String) =
        _uiState.update { it.copy(code = value.filter { ch -> ch.isDigit() }.take(6)) }

    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value) }

    fun sendCode() {
        val state = _uiState.value
        if (!state.canSendCode) {
            if (state.health?.mailConfigured != true) {
                _uiState.update { it.copy(error = "服务端未配置邮件服务，无法发送验证码") }
            }
            return
        }
        val purpose = if (state.authMode == CloudAuthMode.RESET) {
            CloudCodePurpose.RESET
        } else {
            CloudCodePurpose.REGISTER
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            when (val result = cloudRepository.sendCode(state.email, purpose)) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            codeTtlMinutes = result.data.ttlMinutes,
                            message = "验证码已发送，${result.data.ttlMinutes} 分钟内有效"
                        )
                    }
                    startCountdown(result.data.cooldownSeconds)
                }

                is RepositoryResult.Error ->
                    _uiState.update { it.copy(isWorking = false, error = result.message) }
            }
        }
    }

    private fun startCountdown(seconds: Int) {
        countdownJob?.cancel()
        if (seconds <= 0) {
            _uiState.update { it.copy(codeCountdown = 0) }
            return
        }
        countdownJob = viewModelScope.launch {
            var left = seconds
            while (left > 0) {
                _uiState.update { it.copy(codeCountdown = left) }
                delay(1_000)
                left -= 1
            }
            _uiState.update { it.copy(codeCountdown = 0) }
        }
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            val result = when (state.authMode) {
                CloudAuthMode.LOGIN -> cloudRepository.login(state.email, state.password)
                CloudAuthMode.REGISTER -> cloudRepository.register(
                    email = state.email,
                    password = state.password,
                    code = state.code,
                    username = state.username
                )

                CloudAuthMode.RESET -> cloudRepository.resetPassword(
                    email = state.email,
                    code = state.code,
                    password = state.password
                )
            }
            when (result) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(
                        isWorking = false,
                        password = "",
                        code = "",
                        username = "",
                        authMode = CloudAuthMode.LOGIN,
                        message = when (state.authMode) {
                            CloudAuthMode.LOGIN -> "已登录：${result.data.username}"
                            CloudAuthMode.REGISTER -> "注册成功，已登录"
                            CloudAuthMode.RESET -> "密码已重置，已登录"
                        }
                    )
                }

                is RepositoryResult.Error ->
                    _uiState.update { it.copy(isWorking = false, error = result.message) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            cloudRepository.signOut()
            _uiState.update {
                it.copy(
                    backupInfo = null,
                    password = "",
                    code = "",
                    authMode = CloudAuthMode.LOGIN,
                    message = "已退出登录"
                )
            }
        }
    }

    // ── 用户中心 ──────────────────────────────────────────────────────

    fun updateUsername(newName: String) {
        val name = newName.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(error = "用户名不能为空") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            when (val result = cloudRepository.updateUsername(name)) {
                is RepositoryResult.Success ->
                    _uiState.update { it.copy(isWorking = false, message = "用户名已更新") }

                is RepositoryResult.Error ->
                    _uiState.update { it.copy(isWorking = false, error = result.message) }
            }
        }
    }

    fun uploadAvatar(bytes: ByteArray, fileName: String, mimeType: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            when (val result = cloudRepository.uploadAvatar(bytes, fileName, mimeType)) {
                is RepositoryResult.Success ->
                    _uiState.update { it.copy(isWorking = false, message = "头像已更新") }

                is RepositoryResult.Error ->
                    _uiState.update { it.copy(isWorking = false, error = result.message) }
            }
        }
    }

    fun removeAvatar() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            when (val result = cloudRepository.removeAvatar()) {
                is RepositoryResult.Success ->
                    _uiState.update { it.copy(isWorking = false, message = "头像已移除") }

                is RepositoryResult.Error ->
                    _uiState.update { it.copy(isWorking = false, error = result.message) }
            }
        }
    }

    // ── 云端备份 ──────────────────────────────────────────────────────

    fun refreshBackup() {
        viewModelScope.launch { refreshBackupInternal() }
    }

    private suspend fun refreshBackupInternal() {
        _uiState.update { it.copy(isLoadingBackup = true) }
        val info = when (val result = cloudRepository.backupInfo()) {
            is RepositoryResult.Success -> result.data
            // 读不到元信息不弹错：可能只是没登录或网络抖了一下，界面上"没有备份"就够了
            is RepositoryResult.Error -> null
        }
        _uiState.update { it.copy(isLoadingBackup = false, backupInfo = info) }
    }

    /** 导出全量备份并上传到云端。成功后就地更新元信息。 */
    fun uploadBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            val exported = exportBackup.all()
            if (exported.isFailure) {
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        error = "导出失败：${exported.exceptionOrNull()?.message}"
                    )
                }
                return@launch
            }
            val backup = exported.getOrThrow()
            try {
                val fileName = "zhilu-backup-${dateStamp()}.dtk"
                when (val result = cloudRepository.uploadBackup(backup.file, fileName)) {
                    is RepositoryResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isWorking = false,
                                backupInfo = result.data,
                                message = "已上传 ${backup.noteCount} 篇笔记（${formatSize(result.data.size)}）"
                            )
                        }
                    }

                    is RepositoryResult.Error ->
                        _uiState.update { it.copy(isWorking = false, error = result.message) }
                }
            } finally {
                backup.file.delete()
            }
        }
    }

    fun deleteBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            when (val result = cloudRepository.deleteBackup()) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(isWorking = false, backupInfo = null, message = "云端备份已删除")
                }

                is RepositoryResult.Error ->
                    _uiState.update { it.copy(isWorking = false, error = result.message) }
            }
        }
    }

    fun consumeMessages() {
        _uiState.update { it.copy(message = null, error = null) }
    }

    /** 本地就能判定的错误（比如头像太大）—— 不必先传到服务端再被拒。 */
    fun reportLocalError(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    /** `20261017`，与本地导出的文件名风格一致。 */
    private fun dateStamp(): String {
        val now = java.time.LocalDate.now()
        return "%04d%02d%02d".format(now.year, now.monthValue, now.dayOfMonth)
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }

    /**
     * 从云端恢复：下载到缓存后交给 `CreateSheetViewModel` 走已有的
     * 「预览 → 确认 → 导入」流程（那边本来就负责 `.dtk` 导入）。
     *
     * 这里不做导入，也不持有 preview —— 两个导入入口共用一个预览对话框，
     * 才不会出现"云端恢复没有预览、直接覆盖数据库"这种事。
     */
    fun downloadBackupForRestore(onReady: (File) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            when (val result = cloudRepository.downloadBackup()) {
                is RepositoryResult.Success -> {
                    _uiState.update { it.copy(isWorking = false) }
                    onReady(result.data)
                }

                is RepositoryResult.Error ->
                    _uiState.update { it.copy(isWorking = false, error = result.message) }
            }
        }
    }
}
