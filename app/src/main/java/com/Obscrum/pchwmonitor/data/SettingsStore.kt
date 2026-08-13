package com.Obscrum.pchwmonitor.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.Obscrum.pchwmonitor.ui.dashboard.DashboardLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val serverIp: String = "192.168.1.100",
    val serverPort: Int = 8765,
    val authToken: String? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: String? = null,
    val chartWindowSeconds: Int = 60,
    val themePaletteId: String = "default",
    val dashboardLayout: DashboardLayout = DashboardLayout.default(),
)

class SettingsStore(private val dataStore: DataStore<Preferences>) {
    private val keyIp = stringPreferencesKey("server_ip")
    private val keyPort = intPreferencesKey("server_port")
    private val keyAuthToken = stringPreferencesKey("auth_token")
    private val keyTheme = stringPreferencesKey("theme")
    private val keyLanguage = stringPreferencesKey("language")
    private val keyChartWindow = intPreferencesKey("chart_window_seconds")
    private val keyThemePalette = stringPreferencesKey("theme_palette")
    private val keyDashboardLayout = stringPreferencesKey("dashboard_layout")

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            serverIp = prefs[keyIp] ?: "192.168.1.100",
            serverPort = prefs[keyPort] ?: 8765,
            authToken = prefs[keyAuthToken]?.takeIf { it.isNotBlank() },
            theme = runCatching { ThemeMode.valueOf(prefs[keyTheme] ?: "") }.getOrDefault(ThemeMode.SYSTEM),
            language = prefs[keyLanguage],
            chartWindowSeconds = prefs[keyChartWindow] ?: 60,
            themePaletteId = prefs[keyThemePalette]?.takeIf { it.isNotBlank() } ?: "default",
            dashboardLayout = prefs[keyDashboardLayout].let {
                if (it == null) DashboardLayout.default() else DashboardLayout().fromJson(it)
            },
        )
    }

    suspend fun setServerIp(value: String) {
        dataStore.edit { it[keyIp] = value }
    }

    suspend fun setServerPort(value: Int) {
        dataStore.edit { it[keyPort] = value }
    }

    suspend fun setAuthToken(value: String?) {
        dataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(keyAuthToken) else prefs[keyAuthToken] = value
        }
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

    suspend fun setThemePalette(value: String) {
        dataStore.edit { it[keyThemePalette] = value.takeIf { v -> v.isNotBlank() } ?: "default" }
    }

    suspend fun setDashboardLayout(value: DashboardLayout) {
        dataStore.edit { it[keyDashboardLayout] = value.toJson() }
    }
}
