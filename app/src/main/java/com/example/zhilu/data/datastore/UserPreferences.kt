package com.example.zhilu.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.zhilu.domain.model.AiService
import com.example.zhilu.domain.model.AiSettings
import com.example.zhilu.domain.model.AiVendorPreset
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

/**
 * 主题外观（配色方案）。
 *
 * 与 [ThemeMode]（浅色/深色/跟随系统）是**两个正交的维度**：模式决定明暗，
 * 外观决定"长什么样"。切换外观时用户的明暗偏好不应该被重置，反之亦然。
 *
 * 这里同样只放「标识」，色值在 ui 层的 `ThemePalettes` 里 —— 与 [AccentColor] 一个道理。
 * 追加新外观时放在末尾（存的是 [name]）。
 */
enum class ThemePalette {
    /** 纸墨：出厂默认。暖纸底 + 克制的墨蓝/赭石/橄榄，编辑式排版。 */
    PAPER_INK,

    /**
     * 动森：暖奶油底 + 草绿/桃粉/天蓝的柔和圆角风。
     *
     * 参考 `liuyuhong0324/AnimalIslandUI`（动森风 UI 组件库）的观感：
     * 高饱和**只用在点缀**上，底色保持奶油暖白，圆角明显放大（见 `shapesFor`）。
     */
    ANIMAL_ISLAND;

    companion object {
        val DEFAULT: ThemePalette = PAPER_INK

        fun fromRaw(raw: String?): ThemePalette =
            entries.firstOrNull { it.name == raw } ?: DEFAULT
    }
}

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // ignoreUnknownKeys：以后往 AiService 加字段时，旧数据仍读得回来
    private val aiJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val themePaletteKey = stringPreferencesKey("theme_palette")
    private val accentColorKey = stringPreferencesKey("accent_color")
    private val accessibleEmphasisKey = booleanPreferencesKey("accessible_emphasis")
    private val remindersEnabledKey = booleanPreferencesKey("reminders_enabled")
    private val aiSettingsKey = stringPreferencesKey("ai_settings")

    // 旧的单配置键。只在迁移时读一次，之后不再写。
    private val legacyAiProviderKey = stringPreferencesKey("ai_provider")
    private val legacyAiEndpointKey = stringPreferencesKey("ai_endpoint")
    private val legacyAiApiKeyKey = stringPreferencesKey("ai_api_key")
    private val legacyAiModelKey = stringPreferencesKey("ai_model")

    val themeMode: Flow<ThemeMode> = context.userPreferencesStore.data.map { prefs ->
        ThemeMode.fromRaw(prefs[themeModeKey])
    }

    val accentColor: Flow<AccentColor> = context.userPreferencesStore.data.map { prefs ->
        AccentColor.fromRaw(prefs[accentColorKey])
    }

    val themePalette: Flow<ThemePalette> = context.userPreferencesStore.data.map { prefs ->
        ThemePalette.fromRaw(prefs[themePaletteKey])
    }

    val remindersEnabled: Flow<Boolean> = context.userPreferencesStore.data.map { prefs ->
        prefs[remindersEnabledKey] ?: true
    }

    /** 无障碍语义色板（设计文档 §3.9）。默认关闭。 */
    val accessibleEmphasis: Flow<Boolean> = context.userPreferencesStore.data.map { prefs ->
        prefs[accessibleEmphasisKey] ?: false
    }

    /**
     * 全部 AI 配置。
     *
     * 存成一个 JSON 字符串而不是拆成一堆 Preferences 键：服务是**列表**，
     * 拆成键就得自己编号、还得处理删除留下的空洞。JSON 一次序列化，
     * 加字段也只是多一个默认值（`ignoreUnknownKeys` 保证旧数据读得回来）。
     *
     * **顺带迁移旧的单配置**：早先的版本只有一套 `ai_endpoint`/`ai_api_key`/`ai_model`，
     * 老用户升上来不能让他们重填一遍。迁移只做一次（迁移后旧键还在，但不再被读），
     * 且只在 `ai_settings` 为空时执行 —— 否则用户清空服务列表后会"复活"旧配置。
     */
    val aiSettings: Flow<AiSettings> = context.userPreferencesStore.data.map { prefs ->
        val raw = prefs[aiSettingsKey]
        if (raw != null) {
            runCatching { aiJson.decodeFromString<AiSettings>(raw) }.getOrNull() ?: AiSettings.EMPTY
        } else {
            legacyAiSettings(prefs)
        }
    }

    private fun legacyAiSettings(prefs: Preferences): AiSettings {
        val endpoint = prefs[legacyAiEndpointKey].orEmpty()
        val apiKey = prefs[legacyAiApiKeyKey].orEmpty()
        val model = prefs[legacyAiModelKey].orEmpty()
        if (endpoint.isBlank() && apiKey.isBlank() && model.isBlank()) return AiSettings.EMPTY
        val providerId = prefs[legacyAiProviderKey].orEmpty()
        val providerLabel = AiVendorPreset.presets
            .firstOrNull { it.label.equals(providerId, ignoreCase = true) }
            ?.label
            ?: providerId
        val migrated = AiService(
            id = LEGACY_SERVICE_ID,
            name = providerLabel.ifBlank { model.ifBlank { "我的 AI" } },
            provider = providerLabel,
            endpoint = endpoint,
            apiKey = apiKey,
            model = model
        )
        return AiSettings(services = listOf(migrated), activeId = migrated.id)
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

    suspend fun setThemePalette(palette: ThemePalette) {
        context.userPreferencesStore.edit { prefs ->
            prefs[themePaletteKey] = palette.name
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

    suspend fun setAiSettings(settings: AiSettings) {
        context.userPreferencesStore.edit { prefs ->
            prefs[aiSettingsKey] = aiJson.encodeToString(settings)
        }
    }
}

/** 旧单配置迁移后生成的固定 id：稳定即可，不需要唯一。 */
private const val LEGACY_SERVICE_ID = "migrated"
