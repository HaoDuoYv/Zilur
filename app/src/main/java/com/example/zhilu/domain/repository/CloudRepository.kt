package com.example.zhilu.domain.repository

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.CloudBackupInfo
import com.example.zhilu.domain.model.CloudCodePurpose
import com.example.zhilu.domain.model.CloudHealth
import com.example.zhilu.domain.model.CloudSettings
import com.example.zhilu.domain.model.CloudUser
import com.example.zhilu.domain.model.SendCodeInfo
import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * 可选云端服务的入口。
 *
 * **可选**是这套接口的第一约束：所有方法在"没配置服务地址"时都必须优雅失败
 * （返回 `Error("还没有填写服务地址")`），绝不能抛异常把本地功能带崩。
 *
 * 服务地址由用户自己填（自己部署的服务器，也可能是局域网 IP），所以每个动作都
 * 显式依赖当前保存的地址，而不是编译期固定的端点。
 */
interface CloudRepository {

    /** 当前的云端配置（地址 / 令牌 / 资料快照）。默认是 [CloudSettings.EMPTY]。 */
    val settings: Flow<CloudSettings>

    /** 试连一个地址（不必先保存）。返回服务端能力，用来决定界面形态。 */
    suspend fun testConnection(rawUrl: String): RepositoryResult<CloudHealth>

    /**
     * 保存服务地址（会自动补 `http://`、去掉末尾斜杠）。
     *
     * **地址变了就清掉登录态**：服务器 A 的令牌在服务器 B 上毫无意义，
     * 留着只会让界面显示"已登录"却每个请求都 401。
     */
    suspend fun saveServerUrl(rawUrl: String): RepositoryResult<String>

    // ── 账号 ──────────────────────────────────────────────────────────

    suspend fun sendCode(email: String, purpose: CloudCodePurpose): RepositoryResult<SendCodeInfo>

    suspend fun register(
        email: String,
        password: String,
        code: String,
        username: String
    ): RepositoryResult<CloudUser>

    suspend fun login(email: String, password: String): RepositoryResult<CloudUser>

    /** 验证码重置密码，成功后**直接就处于登录态**。 */
    suspend fun resetPassword(
        email: String,
        code: String,
        password: String
    ): RepositoryResult<CloudUser>

    // ── 资料 ──────────────────────────────────────────────────────────

    suspend fun refreshProfile(): RepositoryResult<CloudUser>

    suspend fun updateUsername(username: String): RepositoryResult<CloudUser>

    suspend fun uploadAvatar(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): RepositoryResult<CloudUser>

    suspend fun removeAvatar(): RepositoryResult<CloudUser>

    // ── 云端备份 ───────────────────────────────────────────────────────

    /** 云端那份备份的元信息；`null` = 还没有备份（不是错误）。 */
    suspend fun backupInfo(): RepositoryResult<CloudBackupInfo?>

    suspend fun uploadBackup(source: File, fileName: String): RepositoryResult<CloudBackupInfo>

    /** 下载到缓存目录并返回文件；调用方负责导入后删除。 */
    suspend fun downloadBackup(): RepositoryResult<File>

    suspend fun deleteBackup(): RepositoryResult<Unit>

    /** 退出登录：清令牌与资料快照，**保留服务地址**（下次登录不用重填）。 */
    suspend fun signOut()
}
