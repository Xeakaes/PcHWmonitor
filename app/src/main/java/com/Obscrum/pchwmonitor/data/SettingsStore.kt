package com.Obscrum.pchwmonitor.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val serverIp: String = "192.168.1.100",
    val serverPort: Int = 8765,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: String? = null,
    val chartWindowSeconds: Int = 60,
)

class SettingsStore(private val dataStore: DataStore<Preferences>) {
    private val keyIp = stringPreferencesKey("server_ip")
    private val keyPort = intPreferencesKey("server_port")
    private val keyTheme = stringPreferencesKey("theme")
    private val keyLanguage = stringPreferencesKey("language")
    private val keyChartWindow = intPreferencesKey("chart_window_seconds")

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            serverIp = prefs[keyIp] ?: "192.168.1.100",
            serverPort = prefs[keyPort] ?: 8765,
            theme = runCatching { ThemeMode.valueOf(prefs[keyTheme] ?: "") }.getOrDefault(ThemeMode.SYSTEM),
            language = prefs[keyLanguage],
            chartWindowSeconds = prefs[keyChartWindow] ?: 60,
        )
    }

    suspend fun setServerIp(value: String) {
        dataStore.edit { it[keyIp] = value }
    }

    suspend fun setServerPort(value: Int) {
        dataStore.edit { it[keyPort] = value }
    }

    suspend fun setTheme(value: ThemeMode) {
        dataStore.edit { it[keyTheme] = value.name }
    }

    suspend fun setLanguage(value: String?) {
        dataStore.edit { prefs ->
            if (value == null) prefs.remove(keyLanguage) else prefs[keyLanguage] = value
        }
    }

    suspend fun setChartWindowSeconds(value: Int) {
        dataStore.edit { it[keyChartWindow] = value }
    }
}
