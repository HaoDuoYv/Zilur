package com.example.zhilu.data.cloud

import android.content.Context
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.datastore.CloudPreferences
import com.example.zhilu.domain.model.CloudBackupInfo
import com.example.zhilu.domain.model.CloudCodePurpose
import com.example.zhilu.domain.model.CloudHealth
import com.example.zhilu.domain.model.CloudSettings
import com.example.zhilu.domain.model.CloudUser
import com.example.zhilu.domain.model.SendCodeInfo
import com.example.zhilu.domain.repository.CloudRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 云端仓库：把"地址 + 令牌"的状态管理收在一处，网络调用全部转成 [RepositoryResult]。
 *
 * 失败一律转成 `RepositoryResult.Error` 而不是抛异常 —— 云端是**可选**功能，
 * 任何网络问题都不该让界面崩掉；文案直接取服务端返回的原话（它对用户更有用）。
 */
@Singleton
class CloudRepositoryImpl @Inject constructor(
    private val preferences: CloudPreferences,
    private val api: CloudApiClient,
    @ApplicationContext private val context: Context
) : CloudRepository {

    override val settings: Flow<CloudSettings> = preferences.settings

    override suspend fun testConnection(rawUrl: String): RepositoryResult<CloudHealth> = guarded {
        val base = CloudApiClient.normalizeServerUrl(rawUrl)
        if (base.isEmpty()) throw CloudApiException("请先填写服务地址", 0)
        api.health(base)
    }

    override suspend fun saveServerUrl(rawUrl: String): RepositoryResult<String> = guarded {
        val normalized = CloudApiClient.normalizeServerUrl(rawUrl)
        if (normalized.isEmpty()) {
            // 清空地址 = 明确"不使用云端"。连令牌一起清掉，回到纯粹的本地状态
            preferences.clear()
            return@guarded ""
        }
        val previous = preferences.settings.first()
        val addressChanged = previous.serverUrl.isNotBlank() && previous.serverUrl != normalized
        preferences.update {
            it.copy(
                serverUrl = normalized,
                // 换服务器必须重新登录：旧令牌在新服务器上不存在，留着只会到处 401
                token = if (addressChanged) "" else it.token,
                user = if (addressChanged) null else it.user
            )
        }
        normalized
    }

    override suspend fun sendCode(
        email: String,
        purpose: CloudCodePurpose
    ): RepositoryResult<SendCodeInfo> = guarded {
        val base = requireServer()
        api.sendCode(base, email.trim(), purpose)
    }

    override suspend fun register(
        email: String,
        password: String,
        code: String,
        username: String
    ): RepositoryResult<CloudUser> = guarded {
        val base = requireServer()
        val auth = api.register(
            base = base,
            email = email.trim(),
            password = password,
            code = code.trim(),
            username = username.trim()
        )
        persistSession(base, auth)
    }

    override suspend fun login(email: String, password: String): RepositoryResult<CloudUser> =
        guarded {
            val base = requireServer()
            persistSession(base, api.login(base, email.trim(), password))
        }

    override suspend fun resetPassword(
        email: String,
        code: String,
        password: String
    ): RepositoryResult<CloudUser> = guarded {
        val base = requireServer()
        persistSession(base, api.resetPassword(base, email.trim(), code.trim(), password))
    }

    override suspend fun refreshProfile(): RepositoryResult<CloudUser> = guarded {
        val (base, token) = requireSession()
        val user = api.me(base, token).withAbsoluteAvatar(base)
        preferences.update { it.copy(user = user) }
        user
    }

    override suspend fun updateUsername(username: String): RepositoryResult<CloudUser> = guarded {
        val (base, token) = requireSession()
        val user = api.updateUsername(base, token, username.trim()).withAbsoluteAvatar(base)
        preferences.update { it.copy(user = user) }
        user
    }

    override suspend fun uploadAvatar(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): RepositoryResult<CloudUser> = guarded {
        val (base, token) = requireSession()
        val user = api.uploadAvatar(base, token, bytes, fileName, mimeType).withAbsoluteAvatar(base)
        preferences.update { it.copy(user = user) }
        user
    }

    override suspend fun removeAvatar(): RepositoryResult<CloudUser> = guarded {
        val (base, token) = requireSession()
        val user = api.deleteAvatar(base, token).withAbsoluteAvatar(base)
        preferences.update { it.copy(user = user) }
        user
    }

    override suspend fun backupInfo(): RepositoryResult<CloudBackupInfo?> = guarded {
        val (base, token) = requireSession()
        api.backupInfo(base, token)
    }

    override suspend fun uploadBackup(
        source: File,
        fileName: String
    ): RepositoryResult<CloudBackupInfo> = guarded {
        val (base, token) = requireSession()
        api.uploadBackup(base, token, source, fileName)
    }

    override suspend fun downloadBackup(): RepositoryResult<File> = guarded {
        val (base, token) = requireSession()
        val dest = File(context.cacheDir, "cloud_restore_${System.currentTimeMillis()}.dtk")
        api.downloadBackup(base, token, dest)
    }

    override suspend fun deleteBackup(): RepositoryResult<Unit> = guarded {
        val (base, token) = requireSession()
        api.deleteBackup(base, token)
    }

    override suspend fun signOut() {
        preferences.update { it.copy(token = "", user = null) }
    }

    // ── 内部 ──────────────────────────────────────────────────────────

    private suspend fun requireServer(): String {
        val current = preferences.settings.first()
        if (!current.isConfigured) throw CloudApiException("还没有填写服务地址", 0)
        return current.serverUrl
    }

    private suspend fun requireSession(): Pair<String, String> {
        val current = preferences.settings.first()
        if (!current.isConfigured) throw CloudApiException("还没有填写服务地址", 0)
        if (!current.isSignedIn) throw CloudApiException("请先登录云端账号", 0)
        return current.serverUrl to current.token
    }

    private suspend fun persistSession(base: String, auth: CloudAuthResult): CloudUser {
        val user = auth.user.withAbsoluteAvatar(base)
        preferences.update { it.copy(token = auth.token, user = user) }
        return user
    }

    /** 服务端给的是相对路径，拼上当前地址才能被 Coil 直接加载。 */
    private fun CloudUser.withAbsoluteAvatar(base: String): CloudUser {
        val raw = avatarUrl?.takeIf { it.isNotBlank() } ?: return this
        val absolute = if (raw.startsWith("http://") || raw.startsWith("https://")) {
            raw
        } else {
            base.trimEnd('/') + if (raw.startsWith("/")) raw else "/$raw"
        }
        return copy(avatarUrl = absolute)
    }

    /**
     * 异常 → [RepositoryResult]。**取消要原样抛出**：把 `CancellationException`
     * 吞成 Error 会让"用户退出了页面"变成一条"云端操作失败"的提示。
     */
    private suspend fun <T> guarded(block: suspend () -> T): RepositoryResult<T> =
        try {
            RepositoryResult.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: CloudApiException) {
            RepositoryResult.Error(e.message ?: "云端操作失败", e)
        } catch (e: Exception) {
            RepositoryResult.Error(e.message ?: "云端操作失败", e)
        }
}
