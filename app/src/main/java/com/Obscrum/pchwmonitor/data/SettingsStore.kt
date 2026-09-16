package com.Obscrum.pchwmonitor.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.Obscrum.pchwmonitor.ui.dashboard.DashboardLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val servers: List<ServerConfig> = emptyList(),
    val activeServerId: String? = null,
    // Legacy fields kept for migration only
    val serverIp: String = "192.168.1.100",
    val serverPort: Int = 8765,
    val authToken: String? = null,
    val hostname: String? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: String? = null,
    val chartWindowSeconds: Int = 60,
    val themePaletteId: String = "default",
    val dashboardLayout: DashboardLayout = DashboardLayout.default(),
    val customBackgroundEnabled: Boolean = false,
    val glassmorphismEnabled: Boolean = false,
    val customBackgroundUri: String? = null,
)

class SettingsStore(private val dataStore: DataStore<Preferences>) {
    private val keyIp = stringPreferencesKey("server_ip")
    private val keyPort = intPreferencesKey("server_port")
    private val keyAuthToken = stringPreferencesKey("auth_token")
    private val keyHostname = stringPreferencesKey("hostname")
    private val keyTheme = stringPreferencesKey("theme")
    private val keyLanguage = stringPreferencesKey("language")
    private val keyChartWindow = intPreferencesKey("chart_window_seconds")
    private val keyThemePalette = stringPreferencesKey("theme_palette")
    private val keyDashboardLayout = stringPreferencesKey("dashboard_layout")
    private val keyCustomBackgroundEnabled = stringPreferencesKey("custom_background_enabled")
    private val keyGlassmorphismEnabled = stringPreferencesKey("glassmorphism_enabled")
    private val keyCustomBackgroundUri = stringPreferencesKey("custom_background_uri")
    private val keyServersJson = stringPreferencesKey("servers_json")
    private val keyActiveServerId = stringPreferencesKey("active_server_id")

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        // Migration: build server list from legacy fields if servers_json is null
        val servers = prefs[keyServersJson]?.let {
            runCatching { Json.decodeFromString<List<ServerConfig>>(it) }.getOrNull()
        } ?: run {
            val legacyIp = prefs[keyIp] ?: "192.168.1.100"
            val legacyPort = prefs[keyPort] ?: 8765
            val legacyToken = prefs[keyAuthToken]?.takeIf { it.isNotBlank() }
            val migrated = listOf(ServerConfig(id = "default", name = "My PC", ip = legacyIp, port = legacyPort, token = legacyToken))
            // Write back migration
            dataStore.edit { it[keyServersJson] = Json.encodeToString(migrated) }
            migrated
        }
        val activeId = prefs[keyActiveServerId]?.takeIf { it.isNotBlank() }
            ?: servers.firstOrNull()?.id

        AppSettings(
            servers = servers,
            activeServerId = activeId,
            serverIp = prefs[keyIp] ?: "192.168.1.100",
            serverPort = prefs[keyPort] ?: 8765,
            authToken = prefs[keyAuthToken]?.takeIf { it.isNotBlank() },
            hostname = prefs[keyHostname]?.takeIf { it.isNotBlank() },
            theme = runCatching { ThemeMode.valueOf(prefs[keyTheme] ?: "") }.getOrDefault(ThemeMode.SYSTEM),
            language = prefs[keyLanguage],
            chartWindowSeconds = prefs[keyChartWindow] ?: 60,
            themePaletteId = prefs[keyThemePalette]?.takeIf { it.isNotBlank() } ?: "default",
            dashboardLayout = prefs[keyDashboardLayout].let {
                if (it == null) DashboardLayout.default() else DashboardLayout().fromJson(it)
            },
            customBackgroundEnabled = prefs[keyCustomBackgroundEnabled] == "true",
            glassmorphismEnabled = prefs[keyGlassmorphismEnabled] == "true",
            customBackgroundUri = prefs[keyCustomBackgroundUri]?.takeIf { it.isNotBlank() },
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

    suspend fun setHostname(value: String?) {
        dataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(keyHostname) else prefs[keyHostname] = value
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

    suspend fun setCustomBackgroundEnabled(value: Boolean) {
        dataStore.edit { it[keyCustomBackgroundEnabled] = value.toString() }
    }

    suspend fun setCustomBackgroundUri(value: String?) {
        dataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(keyCustomBackgroundUri) else prefs[keyCustomBackgroundUri] = value
        }
    }

    suspend fun setGlassmorphismEnabled(value: Boolean) {
        dataStore.edit { it[keyGlassmorphismEnabled] = value.toString() }
    }

    // Server list methods
    suspend fun setServers(servers: List<ServerConfig>) {
        dataStore.edit { it[keyServersJson] = Json.encodeToString(servers) }
    }

    suspend fun setActiveServerId(id: String?) {
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(keyActiveServerId) else prefs[keyActiveServerId] = id
        }
    }

    suspend fun addServer(server: ServerConfig) {
        dataStore.edit { prefs ->
            val current = prefs[keyServersJson]?.let {
                runCatching { Json.decodeFromString<List<ServerConfig>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current + server
            prefs[keyServersJson] = Json.encodeToString(updated)
            if (updated.size == 1) prefs[keyActiveServerId] = server.id
        }
    }

    suspend fun removeServer(id: String) {
        dataStore.edit { prefs ->
            val current = prefs[keyServersJson]?.let {
                runCatching { Json.decodeFromString<List<ServerConfig>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current.filter { it.id != id }
            prefs[keyServersJson] = Json.encodeToString(updated)
            if (prefs[keyActiveServerId] == id) {
                val newActive = updated.firstOrNull()?.id
                if (newActive != null) prefs[keyActiveServerId] = newActive
                else prefs.remove(keyActiveServerId)
            }
        }
    }

    suspend fun updateServerCertHash(id: String, certHash: String) {
        dataStore.edit { prefs ->
            val current = prefs[keyServersJson]?.let {
                runCatching { Json.decodeFromString<List<ServerConfig>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current.map { if (it.id == id) it.copy(trustedCertHash = certHash, useTls = true) else it }
            prefs[keyServersJson] = Json.encodeToString(updated)
        }
    }

    suspend fun updateServerHostname(id: String, hostname: String) {
        dataStore.edit { prefs ->
            val current = prefs[keyServersJson]?.let {
                runCatching { Json.decodeFromString<List<ServerConfig>>(it) }.getOrNull()
            } ?: emptyList()
            val updated = current.map { if (it.id == id) it.copy(hostname = hostname) else it }
            prefs[keyServersJson] = Json.encodeToString(updated)
        }
    }

    suspend fun updateServerConnection(id: String, ip: String, port: Int, token: String?, hostname: String? = null) {
        dataStore.edit { prefs ->
            val current = prefs[keyServersJson]?.let {
                runCatching { Json.decodeFromString<List<ServerConfig>>(it) }.getOrNull()
            } ?: emptyList()
            val updated = current.map {
                if (it.id == id) it.copy(ip = ip, port = port, token = token, hostname = hostname?.takeIf { h -> h.isNotBlank() }) else it
            }
            prefs[keyServersJson] = Json.encodeToString(updated)
        }
    }

    /**
     * Atomically update all connection-related settings in a single DataStore edit.
     * This prevents partial updates from triggering syncControllers() with stale values.
     */
    suspend fun saveConnectionAndPreferences(
        ip: String, port: Int, token: String?, hostname: String?,
        theme: ThemeMode, language: String?, chartWindowSeconds: Int,
    ) {
        dataStore.edit { prefs ->
            prefs[keyIp] = ip
            prefs[keyPort] = port
            if (token.isNullOrBlank()) prefs.remove(keyAuthToken) else prefs[keyAuthToken] = token
            if (hostname.isNullOrBlank()) prefs.remove(keyHostname) else prefs[keyHostname] = hostname
            prefs[keyTheme] = theme.name
            if (language == null) prefs.remove(keyLanguage) else prefs[keyLanguage] = language
            prefs[keyChartWindow] = chartWindowSeconds
            // Also update the active server in the servers list
            val activeId = prefs[keyActiveServerId]?.takeIf { it.isNotBlank() }
            if (activeId != null) {
                val current = prefs[keyServersJson]?.let {
                    runCatching { Json.decodeFromString<List<ServerConfig>>(it) }.getOrNull()
                } ?: emptyList()
                val updated = current.map {
                    if (it.id == activeId) it.copy(ip = ip, port = port, token = token, hostname = hostname?.takeIf { h -> h.isNotBlank() }) else it
                }
                prefs[keyServersJson] = Json.encodeToString(updated)
            }
        }
    }
}
