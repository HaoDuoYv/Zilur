package com.example.zhilu.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val remindersEnabledKey = booleanPreferencesKey("reminders_enabled")

    val themeMode: Flow<ThemeMode> = context.userPreferencesStore.data.map { prefs ->
        ThemeMode.fromRaw(prefs[themeModeKey])
    }

    val remindersEnabled: Flow<Boolean> = context.userPreferencesStore.data.map { prefs ->
        prefs[remindersEnabledKey] ?: true
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.userPreferencesStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        context.userPreferencesStore.edit { prefs ->
            prefs[remindersEnabledKey] = enabled
        }
    }
}
