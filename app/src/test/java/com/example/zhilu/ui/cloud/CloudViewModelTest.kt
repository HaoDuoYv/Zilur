package com.example.zhilu.ui.cloud

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.cloud.CloudApiClient
import com.example.zhilu.domain.model.CloudBackupInfo
import com.example.zhilu.domain.model.CloudCodePurpose
import com.example.zhilu.domain.model.CloudHealth
import com.example.zhilu.domain.model.CloudSettings
import com.example.zhilu.domain.model.CloudUser
import com.example.zhilu.domain.model.SendCodeInfo
import com.example.zhilu.domain.repository.CloudRepository
import com.example.zhilu.domain.usecase.ExportBackupUseCase
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 云端页的状态机。
 *
 * 重点不是在测网络（那由服务端冒烟脚本覆盖），而是三件事：
 * ① 服务端返回的原话要**如实传到界面**（用户靠它判断是密码错还是服务地址错）；
 * ② 成功后要清掉密码/验证码，别把明文密码留在内存状态里；
 * ③ 未配置时不能假装成功。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CloudViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        settings: CloudSettings = CloudSettings.EMPTY
    ): Pair<CloudViewModel, FakeCloudRepository> {
        val repo = FakeCloudRepository().apply { state.value = settings }
        // 注意：仓库状态必须在构造 ViewModel **之前**准备好 —— init 里那次
        // "已配置就探一次能力"读的是当时的快照
        return CloudViewModel(repo, mockk<ExportBackupUseCase>(relaxed = true)) to repo
    }

    @Test
    fun `未配置地址时登录失败并展示仓库给的原因`() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("password123")
        advanceUntilIdle()

        vm.submit()
        advanceUntilIdle()

        assertEquals("还没有填写服务地址", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSignedIn)
    }

    @Test
    fun `登录成功后进入登录态并清空密码`() = runTest(dispatcher) {
        val (vm, repo) = viewModel(CloudSettings(serverUrl = "http://x:8787"))
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("password123")
        advanceUntilIdle()

        vm.submit()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isSignedIn)
        assertEquals("a@b.com", state.user?.email)
        assertEquals("", state.password)
        assertTrue(state.message?.contains("已登录") == true)
    }

    @Test
    fun `服务端拒绝时把原话显示出来`() = runTest(dispatcher) {
        val (vm, repo) = viewModel(CloudSettings(serverUrl = "http://x:8787"))
        repo.nextError = "邮箱或密码不正确"
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("password123")
        advanceUntilIdle()

        vm.submit()
        advanceUntilIdle()

        assertEquals("邮箱或密码不正确", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSignedIn)
    }

    @Test
    fun `找回密码模式必须凑满 6 位验证码才能提交`() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("password123")
        vm.switchAuthMode(CloudAuthMode.RESET)
        vm.onCodeChange("123")
        advanceUntilIdle()

        assertFalse("验证码不足 6 位时不该允许提交", vm.uiState.value.canSubmit)

        vm.onCodeChange("123456")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canSubmit)
    }

    @Test
    fun `注册成功后清空验证码与用户名`() = runTest(dispatcher) {
        val (vm, repo) = viewModel(CloudSettings(serverUrl = "http://x:8787"))
        repo.health = CloudHealth(version = "1.0.0", mailConfigured = true, requireEmailVerification = true)
        vm.switchAuthMode(CloudAuthMode.REGISTER)
        vm.onEmailChange("new@b.com")
        vm.onPasswordChange("password123")
        vm.onCodeChange("654321")
        vm.onUsernameChange("新用户")
        advanceUntilIdle()

        vm.submit()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isSignedIn)
        assertEquals("", state.code)
        assertEquals("", state.username)
        assertEquals("", state.password)
    }

    @Test
    fun `获取验证码成功后进入冷却`() = runTest(dispatcher) {
        val (vm, repo) = viewModel(CloudSettings(serverUrl = "http://x:8787"))
        repo.health = CloudHealth(version = "1.0.0", mailConfigured = true, requireEmailVerification = true)
        vm.switchAuthMode(CloudAuthMode.REGISTER)
        vm.onEmailChange("new@b.com")
        advanceUntilIdle()

        vm.sendCode()
        // 只推进"当下"，不要 advanceUntilIdle：那会把 60 秒冷却一次快进完，
        // 倒计时自然归零，测出来的永远是 0
        runCurrent()

        assertEquals(60, vm.uiState.value.codeCountdown)
        assertFalse("倒计时期间不能再点", vm.uiState.value.canSendCode)
        // init 里那次能力探测也会记一笔，所以只断言"注册用途的验证码请求发出去了"
        assertTrue(repo.calls.contains("sendCode:register"))
    }

    @Test
    fun `服务端没配邮件时不发请求，直接提示`() = runTest(dispatcher) {
        val (vm, repo) = viewModel(CloudSettings(serverUrl = "http://x:8787"))
        repo.health = CloudHealth(version = "1.0.0", mailConfigured = false)
        vm.switchAuthMode(CloudAuthMode.REGISTER)
        vm.onEmailChange("new@b.com")
        advanceUntilIdle()

        vm.sendCode()
        advanceUntilIdle()

        assertEquals("服务端未配置邮件服务，无法发送验证码", vm.uiState.value.error)
        assertTrue(repo.calls.none { it.startsWith("sendCode") })
    }

    @Test
    fun `修改用户名成功后给出提示`() = runTest(dispatcher) {
        val (vm, repo) = viewModel(
            CloudSettings(serverUrl = "http://x:8787", token = "t", user = USER)
        )
        advanceUntilIdle()

        vm.updateUsername("改过的名字")
        advanceUntilIdle()

        assertEquals("改过的名字", vm.uiState.value.user?.username)
        assertEquals("用户名已更新", vm.uiState.value.message)
    }

    @Test
    fun `退出登录清空资料但保留服务地址`() = runTest(dispatcher) {
        val (vm, repo) = viewModel(
            CloudSettings(serverUrl = "http://x:8787", token = "t", user = USER)
        )
        advanceUntilIdle()

        vm.signOut()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isSignedIn)
        assertNull(state.user)
        assertNull(state.backupInfo)
        assertEquals("http://x:8787", state.settings.serverUrl)
    }

    @Test
    fun `保存地址后会自动探测服务端能力`() = runTest(dispatcher) {
        val (vm, repo) = viewModel()
        repo.health = CloudHealth(version = "9.9.9", mailConfigured = true)
        vm.onServerUrlChange("192.168.1.10:8787")
        advanceUntilIdle()

        vm.saveServerUrl()
        advanceUntilIdle()

        assertEquals("http://192.168.1.10:8787", vm.uiState.value.settings.serverUrl)
        assertEquals("服务端版本应被探到", "9.9.9", vm.uiState.value.health?.version)
    }
}

private val USER = CloudUser(id = 1, email = "a@b.com", username = "测试用户")

/**
 * 手写 fake：`mockk(relaxed = true)` 造不出 `Result` / `Flow` 的正确形态，
 * 而且这里要断言"调了哪些接口、顺序如何"，记录型 fake 更合适。
 */
private class FakeCloudRepository : CloudRepository {
    val state = MutableStateFlow(CloudSettings.EMPTY)
    override val settings: Flow<CloudSettings> = state

    val calls = mutableListOf<String>()
    var health: CloudHealth = CloudHealth(version = "1.0.0")
    var nextError: String? = null
    var backup: CloudBackupInfo? = null

    private fun failure(): RepositoryResult.Error? =
        nextError?.let { RepositoryResult.Error(it) }

    private fun serviceError(): RepositoryResult.Error? =
        if (!state.value.isConfigured) RepositoryResult.Error("还没有填写服务地址") else failure()

    private fun sessionError(): RepositoryResult.Error? = when {
        !state.value.isConfigured -> RepositoryResult.Error("还没有填写服务地址")
        !state.value.isSignedIn -> RepositoryResult.Error("请先登录云端账号")
        else -> failure()
    }

    override suspend fun testConnection(rawUrl: String): RepositoryResult<CloudHealth> {
        calls += "test:$rawUrl"
        return failure() ?: RepositoryResult.Success(health)
    }

    override suspend fun saveServerUrl(rawUrl: String): RepositoryResult<String> {
        calls += "save:$rawUrl"
        val normalized = CloudApiClient.normalizeServerUrl(rawUrl)
        state.update { it.copy(serverUrl = normalized) }
        return failure() ?: RepositoryResult.Success(normalized)
    }

    override suspend fun sendCode(
        email: String,
        purpose: CloudCodePurpose
    ): RepositoryResult<SendCodeInfo> {
        calls += "sendCode:${purpose.wire}"
        serviceError()?.let { return it }
        return RepositoryResult.Success(SendCodeInfo(cooldownSeconds = 60, ttlMinutes = 10))
    }

    override suspend fun register(
        email: String,
        password: String,
        code: String,
        username: String
    ): RepositoryResult<CloudUser> {
        calls += "register:$email"
        serviceError()?.let { return it }
        val user = CloudUser(id = 2, email = email, username = username.ifBlank { "新用户" })
        state.update { it.copy(token = "t", user = user) }
        return RepositoryResult.Success(user)
    }

    override suspend fun login(email: String, password: String): RepositoryResult<CloudUser> {
        calls += "login:$email"
        serviceError()?.let { return it }
        val user = CloudUser(id = 1, email = email, username = "测试用户")
        state.update { it.copy(token = "t", user = user) }
        return RepositoryResult.Success(user)
    }

    override suspend fun resetPassword(
        email: String,
        code: String,
        password: String
    ): RepositoryResult<CloudUser> {
        calls += "reset:$email"
        serviceError()?.let { return it }
        state.update { it.copy(token = "t", user = USER) }
        return RepositoryResult.Success(USER)
    }

    override suspend fun refreshProfile(): RepositoryResult<CloudUser> =
        sessionError() ?: RepositoryResult.Success(state.value.user ?: USER)

    override suspend fun updateUsername(username: String): RepositoryResult<CloudUser> {
        calls += "updateUsername:$username"
        sessionError()?.let { return it }
        val user = (state.value.user ?: USER).copy(username = username)
        state.update { it.copy(user = user) }
        return RepositoryResult.Success(user)
    }

    override suspend fun uploadAvatar(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): RepositoryResult<CloudUser> {
        calls += "uploadAvatar"
        sessionError()?.let { return it }
        val user = (state.value.user ?: USER).copy(avatarUrl = "http://x/avatars/a.png")
        state.update { it.copy(user = user) }
        return RepositoryResult.Success(user)
    }

    override suspend fun removeAvatar(): RepositoryResult<CloudUser> {
        calls += "removeAvatar"
        sessionError()?.let { return it }
        val user = (state.value.user ?: USER).copy(avatarUrl = null)
        state.update { it.copy(user = user) }
        return RepositoryResult.Success(user)
    }

    override suspend fun backupInfo(): RepositoryResult<CloudBackupInfo?> {
        calls += "backupInfo"
        return sessionError() ?: RepositoryResult.Success(backup)
    }

    override suspend fun uploadBackup(
        source: File,
        fileName: String
    ): RepositoryResult<CloudBackupInfo> {
        calls += "uploadBackup:$fileName"
        sessionError()?.let { return it }
        val info = CloudBackupInfo(fileName = fileName, size = 1024, updatedAt = 1L)
        backup = info
        return RepositoryResult.Success(info)
    }

    override suspend fun downloadBackup(): RepositoryResult<File> {
        calls += "downloadBackup"
        return sessionError() ?: RepositoryResult.Success(File("backup.dtk"))
    }

    override suspend fun deleteBackup(): RepositoryResult<Unit> {
        calls += "deleteBackup"
        sessionError()?.let { return it }
        backup = null
        return RepositoryResult.Success(Unit)
    }

    override suspend fun signOut() {
        calls += "signOut"
        state.update { it.copy(token = "", user = null) }
    }
}
