package com.example.zhilu.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.zhilu.domain.model.CloudSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.cloudSettingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "cloud_settings"
)

/**
 * 云端连接配置的持久化。
 *
 * 与 `ai_settings` 同一个思路：**整体一个 JSON 存一个 key**。字段一共三个
 * （地址 / 令牌 / 资料快照）但以后会加（比如同步开关），拆 key 就要为每个字段写迁移。
 *
 * 单独一个 DataStore 文件而不是塞进 `user_preferences`：这里存着登录令牌，
 * 与"主题、提醒开关"那类偏好不同类；出问题时也可以整个文件删掉当"重置云端"。
 */
@Singleton
class CloudPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val settingsKey = stringPreferencesKey("cloud_settings")

    val settings: Flow<CloudSettings> = context.cloudSettingsStore.data.map { prefs ->
        prefs.decode()
    }

    /** 读改写。用同一个 [CloudSettings] 变换而不是"分字段写"，避免两处并发写互相覆盖。 */
    suspend fun update(transform: (CloudSettings) -> CloudSettings) {
        context.cloudSettingsStore.edit { prefs ->
            val current = prefs.decode()
            prefs[settingsKey] = json.encodeToString(CloudSettings.serializer(), transform(current))
        }
    }

    /** 彻底清空（换了服务地址、或用户主动重置云端配置时用）。 */
    suspend fun clear() {
        context.cloudSettingsStore.edit { prefs -> prefs.remove(settingsKey) }
    }

    private fun Preferences.decode(): CloudSettings {
        val raw = this[settingsKey] ?: return CloudSettings.EMPTY
        // 解析失败按"没配置"处理：一份坏 JSON 不该让 App 起不来
        return runCatching { json.decodeFromString(CloudSettings.serializer(), raw) }
            .getOrDefault(CloudSettings.EMPTY)
    }
}
