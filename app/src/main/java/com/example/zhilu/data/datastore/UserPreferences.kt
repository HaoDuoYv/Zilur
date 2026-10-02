package com.example.zhilu.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.zhilu.domain.model.AiConfig
import com.example.zhilu.domain.model.AiProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPreferencesStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromRaw(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

/**
 * 应用强调色。
 *
 * 这里只放「标识」，具体色值在 ui 层的 `AccentPalette` 里。之所以分开：
 * DataStore 只需要一个稳定的名字来持久化，色值随设计调整时不应该动到存储层。
 *
 * [INK] 是出厂默认，色值等于主题原本的主色；追加新色时请把新项放在末尾，
 * 避免打乱既有用户的持久化值（存的是 [name]）。
 */
enum class AccentColor {
    /** 墨蓝：出厂默认，与最初的纸墨主题一致。 */
    INK,

    /** 黛紫。 */
    VIOLET,

    /** 松石青。 */
    TEAL,

    /** 苔绿。 */
    MOSS,

    /** 赭土黄。 */
    OCHRE,

    /** 绛红。 */
    CRIMSON,

    /** 石墨灰：最接近「无彩色」的一档。 */
    GRAPHITE,

    /**
     * 正红：唯一"喊出来"的一档，用于需要一眼看到重点的场合。
     * 它同时也是「注意」语义色的来源，所以卡片身份色用的是另一个更暗的红（朱红）。
     */
    SCARLET;

    companion object {
        val DEFAULT: AccentColor = INK

        fun fromRaw(raw: String?): AccentColor =
            entries.firstOrNull { it.name == raw } ?: DEFAULT
    }
}

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val accentColorKey = stringPreferencesKey("accent_color")
    private val accessibleEmphasisKey = booleanPreferencesKey("accessible_emphasis")
    private val remindersEnabledKey = booleanPreferencesKey("reminders_enabled")
    private val aiProviderKey = stringPreferencesKey("ai_provider")
    private val aiEndpointKey = stringPreferencesKey("ai_endpoint")
    private val aiApiKeyKey = stringPreferencesKey("ai_api_key")
    private val aiModelKey = stringPreferencesKey("ai_model")
    private val aiVisionModelKey = stringPreferencesKey("ai_vision_model")

    val themeMode: Flow<ThemeMode> = context.userPreferencesStore.data.map { prefs ->
        ThemeMode.fromRaw(prefs[themeModeKey])
    }

    val accentColor: Flow<AccentColor> = context.userPreferencesStore.data.map { prefs ->
        AccentColor.fromRaw(prefs[accentColorKey])
    }

    val remindersEnabled: Flow<Boolean> = context.userPreferencesStore.data.map { prefs ->
        prefs[remindersEnabledKey] ?: true
    }

    /** 无障碍语义色板（设计文档 §3.9）。默认关闭。 */
    val accessibleEmphasis: Flow<Boolean> = context.userPreferencesStore.data.map { prefs ->
        prefs[accessibleEmphasisKey] ?: false
    }

    val aiConfig: Flow<AiConfig> = context.userPreferencesStore.data.map { prefs ->
        AiConfig(
            provider = prefs[aiProviderKey] ?: AiProvider.DEFAULT_ID,
            endpoint = prefs[aiEndpointKey] ?: "",
            apiKey = prefs[aiApiKeyKey] ?: "",
            model = prefs[aiModelKey] ?: "",
            visionModel = prefs[aiVisionModelKey] ?: ""
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.userPreferencesStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }

    suspend fun setAccentColor(accent: AccentColor) {
        context.userPreferencesStore.edit { prefs ->
            prefs[accentColorKey] = accent.name
        }
    }

    suspend fun setAccessibleEmphasis(enabled: Boolean) {
        context.userPreferencesStore.edit { prefs ->
            prefs[accessibleEmphasisKey] = enabled
        }
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        context.userPreferencesStore.edit { prefs ->
            prefs[remindersEnabledKey] = enabled
        }
    }

    suspend fun setAiConfig(config: AiConfig) {
        context.userPreferencesStore.edit { prefs ->
            prefs[aiProviderKey] = config.provider
            prefs[aiEndpointKey] = config.endpoint
            prefs[aiApiKeyKey] = config.apiKey
            prefs[aiModelKey] = config.model
            prefs[aiVisionModelKey] = config.visionModel
        }
    }
}
