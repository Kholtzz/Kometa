package com.kometa.reader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UiPrefs(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val gridColumns: Int = 3,
    val alwaysWebView: Boolean = false,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val Context.store by preferencesDataStore("settings")
    private val keyTheme = stringPreferencesKey("theme")
    private val keyColumns = intPreferencesKey("columns")
    private val keyWebView = booleanPreferencesKey("always_webview")

    val prefs: Flow<UiPrefs> = context.store.data.map { p ->
        UiPrefs(
            theme = runCatching { ThemeMode.valueOf(p[keyTheme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            gridColumns = p[keyColumns] ?: 3,
            alwaysWebView = p[keyWebView] ?: false,
        )
    }

    suspend fun setTheme(m: ThemeMode) = context.store.edit { it[keyTheme] = m.name }
    suspend fun setColumns(n: Int) = context.store.edit { it[keyColumns] = n }
    suspend fun setAlwaysWebView(b: Boolean) = context.store.edit { it[keyWebView] = b }
}
