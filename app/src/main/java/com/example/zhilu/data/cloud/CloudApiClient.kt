package com.example.zhilu.data.cloud

import com.example.zhilu.domain.model.CloudBackupInfo
import com.example.zhilu.domain.model.CloudCodePurpose
import com.example.zhilu.domain.model.CloudHealth
import com.example.zhilu.domain.model.CloudUser
import com.example.zhilu.domain.model.SendCodeInfo
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

/**
 * 云端接口异常。[code] 是 HTTP 状态码，网络层失败为 -1、本地未配置为 0。
 *
 * 界面直接展示 [message]（服务端返回的原话优先），所以文案必须是给用户看的。
 */
class CloudApiException(
    message: String,
    val code: Int = -1,
    cause: Throwable? = null
) : Exception(message, cause)

/** 登录 / 注册 / 重置密码的统一返回。 */
data class CloudAuthResult(val token: String, val user: CloudUser)

/**
 * 知录云端服务的 HTTP 客户端（OkHttp，非流式）。
 *
 * 与 `LlmApiClient` 保持同一套习惯：显式超时、`withContext(IO)`、异常带状态码。
 * 刻意不引 Retrofit：这里的接口一共十来个，手写反而少一层依赖与注解处理。
 *
 * **每个方法都显式传 `base`**（服务地址）而不是读全局状态：用户在设置页里
 * 「测试连接」时填的地址还没保存，也要能试连。
 */
class CloudApiClient @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // 备份可能有几十 MB，读超时给宽一点；再长就该走分片了，不在本轮范围
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    // ── 探活 ──────────────────────────────────────────────────────────

    suspend fun health(base: String): CloudHealth {
        val text = call(request(base, "/api/health"))
        val root = json.parseToJsonElement(text).jsonObject
        return CloudHealth(
            version = root.string("version"),
            mailConfigured = root.bool("mailConfigured"),
            registrationEnabled = root.bool("registrationEnabled", default = true),
            requireEmailVerification = root.bool("requireEmailVerification")
        )
    }

    // ── 账号 ──────────────────────────────────────────────────────────

    suspend fun sendCode(base: String, email: String, purpose: CloudCodePurpose): SendCodeInfo {
        val body = buildJsonObject {
            put("email", email)
            put("purpose", purpose.wire)
        }
        val text = call(request(base, "/api/auth/send-code"), jsonBody(body), method = "POST")
        val root = json.parseToJsonElement(text).jsonObject
        return SendCodeInfo(
            cooldownSeconds = root.int("cooldownSeconds", 60),
            ttlMinutes = root.int("ttlMinutes", 10)
        )
    }

    suspend fun register(
        base: String,
        email: String,
        password: String,
        code: String?,
        username: String?
    ): CloudAuthResult {
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
            code?.takeIf { it.isNotBlank() }?.let { put("code", it) }
            username?.takeIf { it.isNotBlank() }?.let { put("username", it) }
        }
        val text = call(request(base, "/api/auth/register"), jsonBody(body), method = "POST")
        return parseAuth(text)
    }

    suspend fun login(base: String, email: String, password: String): CloudAuthResult {
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
        }
        val text = call(request(base, "/api/auth/login"), jsonBody(body), method = "POST")
        return parseAuth(text)
    }

    suspend fun resetPassword(
        base: String,
        email: String,
        code: String,
        password: String
    ): CloudAuthResult {
        val body = buildJsonObject {
            put("email", email)
            put("code", code)
            put("password", password)
        }
        val text = call(request(base, "/api/auth/reset-password"), jsonBody(body), method = "POST")
        return parseAuth(text)
    }

    // ── 资料 ──────────────────────────────────────────────────────────

    suspend fun me(base: String, token: String): CloudUser =
        parseUser(call(request(base, "/api/me", token)))

    suspend fun updateUsername(base: String, token: String, username: String): CloudUser {
        val body = buildJsonObject { put("username", username) }
        return parseUser(call(request(base, "/api/me", token), jsonBody(body), method = "PATCH"))
    }

    suspend fun uploadAvatar(
        base: String,
        token: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): CloudUser {
        val part = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("avatar", fileName, bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        return parseUser(call(request(base, "/api/me/avatar", token), part, method = "POST"))
    }

    suspend fun deleteAvatar(base: String, token: String): CloudUser =
        parseUser(call(request(base, "/api/me/avatar", token), method = "DELETE"))

    // ── 云端备份 ───────────────────────────────────────────────────────

    suspend fun backupInfo(base: String, token: String): CloudBackupInfo? {
        val text = call(request(base, "/api/backup", token))
        val element = json.parseToJsonElement(text).jsonObject["backup"] ?: return null
        if (element is kotlinx.serialization.json.JsonNull) return null
        return json.decodeFromJsonElement<CloudBackupInfo>(element)
    }

    suspend fun uploadBackup(
        base: String,
        token: String,
        file: File,
        fileName: String
    ): CloudBackupInfo {
        val part = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("backup", fileName, file.asRequestBody("application/zip".toMediaType()))
            .build()
        val text = call(request(base, "/api/backup", token), part, method = "PUT")
        val element = json.parseToJsonElement(text).jsonObject["backup"]
            ?: throw CloudApiException("服务器没有返回备份信息")
        return json.decodeFromJsonElement<CloudBackupInfo>(element)
    }

    /** 下载到 [dest]（调用方负责给一个可写的临时文件）。 */
    suspend fun downloadBackup(base: String, token: String, dest: File): File =
        withContext(Dispatchers.IO) {
            val request = request(base, "/api/backup/download", token).get().build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw CloudApiException(errorMessage(response.body?.string().orEmpty(), response.code), response.code)
                    }
                    val body = response.body ?: throw CloudApiException("服务器返回了空响应")
                    body.byteStream().use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    dest
                }
            } catch (e: CloudApiException) {
                throw e
            } catch (e: IOException) {
                dest.delete()
                throw CloudApiException("下载失败：${e.message ?: "网络异常"}", -1, e)
            }
        }

    suspend fun deleteBackup(base: String, token: String) {
        call(request(base, "/api/backup", token), method = "DELETE")
    }

    // ── 内部 ──────────────────────────────────────────────────────────

    private fun request(base: String, path: String, token: String? = null): Request.Builder =
        Request.Builder()
            .url("${trimBase(base)}$path")
            .header("Accept", "application/json")
            .apply { token?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }

    private fun jsonBody(body: JsonObject): RequestBody =
        json.encodeToString(JsonObject.serializer(), body)
            .toRequestBody("application/json; charset=utf-8".toMediaType())

    private suspend fun call(
        builder: Request.Builder,
        body: RequestBody? = null,
        method: String = "GET"
    ): String = withContext(Dispatchers.IO) {
        val request = builder.method(method, body).build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw CloudApiException(errorMessage(text, response.code), response.code)
                }
                text
            }
        } catch (e: CloudApiException) {
            throw e
        } catch (e: IOException) {
            Timber.w(e, "云端请求失败：%s %s", method, request.url)
            throw CloudApiException("连不上服务器，请检查地址与网络", -1, e)
        }
    }

    private fun parseAuth(text: String): CloudAuthResult {
        val root = json.parseToJsonElement(text).jsonObject
        val token = root.string("token")
        val userElement = root["user"] ?: throw CloudApiException("服务器没有返回账号信息")
        return CloudAuthResult(
            token = token,
            user = json.decodeFromJsonElement<CloudUser>(userElement)
        )
    }

    private fun parseUser(text: String): CloudUser {
        val element = json.parseToJsonElement(text).jsonObject["user"]
            ?: throw CloudApiException("服务器没有返回账号信息")
        return json.decodeFromJsonElement<CloudUser>(element)
    }

    /**
     * 从错误载荷里取服务端原话（`{"error":{"message":"..."}}`）。
     * 取不到就按状态码给一句能看懂的兜底 —— 界面上直接展示这句话。
     */
    private fun errorMessage(text: String, status: Int): String {
        val parsed = runCatching {
            (json.parseToJsonElement(text).jsonObject["error"] as? JsonObject)
                ?.get("message")
                ?.jsonPrimitive
                ?.content
        }.getOrNull()
        if (!parsed.isNullOrBlank()) return parsed
        return when (status) {
            401 -> "登录已过期，请重新登录"
            403 -> "服务器不允许这个操作"
            404 -> "接口不存在，请确认服务地址"
            413 -> "文件超出服务器允许的大小"
            429 -> "操作过于频繁，请稍后再试"
            else -> "服务器返回错误（$status）"
        }
    }

    private fun trimBase(base: String): String = base.trim().trimEnd('/')

    private fun JsonObject.string(name: String, default: String = ""): String =
        (this[name] as? JsonElement)?.jsonPrimitive?.content ?: default

    private fun JsonObject.bool(name: String, default: Boolean = false): Boolean =
        (this[name] as? JsonElement)?.jsonPrimitive?.booleanOrNull ?: default

    private fun JsonObject.int(name: String, default: Int): Int =
        (this[name] as? JsonElement)?.jsonPrimitive?.intOrNull ?: default

    companion object {
        /**
         * 把用户填的地址规范成可用形态。
         *
         * 用户会填 `192.168.1.10:8787`、`example.com`、`http://x/` 各种写法 ——
         * 少了协议就补 `http://`（自建局域网是 http 为主），末尾斜杠一律去掉
         * （否则拼出来是 `//api/health`，某些反代会 404）。
         */
        fun normalizeServerUrl(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            // 协议统一小写：`HTTP://x` 与 `http://x` 是同一个地址，存成两种写法
            // 会让"地址变了就清登录态"的判定误判成换服务器
            val withScheme = when {
                trimmed.startsWith("http://", ignoreCase = true) -> "http://" + trimmed.substring(7)
                trimmed.startsWith("https://", ignoreCase = true) -> "https://" + trimmed.substring(8)
                else -> "http://$trimmed"
            }
            return withScheme.trimEnd('/')
        }
    }
}
