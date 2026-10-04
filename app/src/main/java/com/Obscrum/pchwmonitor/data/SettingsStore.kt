package com.Obscrum.pchwmonitor.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
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
    val backgroundConnectAll: Boolean = false,
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
    private val keyBackgroundConnectAll = stringPreferencesKey("background_connect_all")

    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        // Migration: build server list from legacy fields only when servers_json is absent
        // and legacy connection data actually exists. A present-but-undecodable value is
        // preserved as-is (decode failure => empty list, never a migration write).
        val servers = if (prefs[keyServersJson] == null) {
            val hasLegacy = prefs[keyIp] != null || prefs[keyPort] != null || prefs[keyAuthToken] != null
            if (hasLegacy) {
                val legacyIp = prefs[keyIp] ?: "192.168.1.100"
                val legacyPort = prefs[keyPort] ?: 8765
                val legacyToken = prefs[keyAuthToken]?.takeIf { it.isNotBlank() }
                val migrated = listOf(ServerConfig(id = "default", name = "My PC", ip = legacyIp, port = legacyPort, token = legacyToken))
                // Write back migration
                dataStore.edit { it[keyServersJson] = json.encodeToString(migrated) }
                migrated
            } else {
                emptyList()
            }
        } else {
            decodeServers(prefs)
        }
        val activeId = prefs[keyActiveServerId]?.takeIf { id -> servers.any { it.id == id } }
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
            backgroundConnectAll = prefs[keyBackgroundConnectAll] == "true",
        )
    }

    private fun decodeServers(prefs: Preferences): List<ServerConfig> =
        prefs[keyServersJson]?.let { raw ->
            runCatching { json.decodeFromString<List<ServerConfig>>(raw) }.getOrElse { emptyList() }
        } ?: emptyList()

    private fun resolveActive(prefs: Preferences, servers: List<ServerConfig>): ServerConfig? {
        val activeId = prefs[keyActiveServerId]?.takeIf { id -> servers.any { it.id == id } }
            ?: servers.firstOrNull()?.id
        return servers.firstOrNull { it.id == activeId }
    }

    private fun MutablePreferences.mirrorLegacy(active: ServerConfig?) {
        if (active == null) return
        this[keyIp] = active.ip
        this[keyPort] = active.port
        val token = active.token
        if (token.isNullOrBlank()) this.remove(keyAuthToken) else this[keyAuthToken] = token
        val hostname = active.hostname
        if (hostname.isNullOrBlank()) this.remove(keyHostname) else this[keyHostname] = hostname
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

    suspend fun setBackgroundConnectAll(value: Boolean) {
        dataStore.edit { it[keyBackgroundConnectAll] = value.toString() }
    }

    suspend fun savePreferences(theme: ThemeMode, language: String?, chartWindowSeconds: Int, themePaletteId: String) {
        dataStore.edit { prefs ->
            prefs[keyTheme] = theme.name
            if (language == null) prefs.remove(keyLanguage) else prefs[keyLanguage] = language
            prefs[keyChartWindow] = chartWindowSeconds
            prefs[keyThemePalette] = themePaletteId
        }
    }

    // Server list methods
    suspend fun setActiveServerId(id: String?) {
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(keyActiveServerId) else prefs[keyActiveServerId] = id
            prefs.mirrorLegacy(resolveActive(prefs, decodeServers(prefs)))
        }
    }

    suspend fun addServer(server: ServerConfig): Result<Unit> = runCatching {
        dataStore.edit { prefs ->
            val current = decodeServers(prefs)
            val issues = ServerValidator.validate(
                ip = server.ip,
                port = server.port,
                name = server.name,
                hostname = server.hostname,
                existingNames = current.map { it.name },
            )
            if (issues.any { it.severity == IssueSeverity.ERROR }) {
                throw IllegalArgumentException(issues.toString())
            }
            val updated = current + server
            prefs[keyServersJson] = json.encodeToString(updated)
            prefs[keyActiveServerId] = server.id
            prefs.mirrorLegacy(server)
        }
    }.map { }

    suspend fun removeServer(id: String) {
        dataStore.edit { prefs ->
            val current = decodeServers(prefs)
            val updated = current.filter { it.id != id }
            prefs[keyServersJson] = json.encodeToString(updated)
            if (prefs[keyActiveServerId] == id) {
                val newActive = updated.firstOrNull()?.id
                if (newActive != null) prefs[keyActiveServerId] = newActive
                else prefs.remove(keyActiveServerId)
            }
            prefs.mirrorLegacy(resolveActive(prefs, updated))
        }
    }

    suspend fun updateServerCertHash(id: String, certHash: String) {
        dataStore.edit { prefs ->
            val current = decodeServers(prefs)
            val updated = current.map { if (it.id == id) it.copy(trustedCertHash = certHash, useTls = true) else it }
            prefs[keyServersJson] = json.encodeToString(updated)
        }
    }

    suspend fun updateServerConnection(
        id: String,
        name: String,
        ip: String,
        port: Int,
        token: String?,
        hostname: String?,
    ): Result<Unit> = runCatching {
        dataStore.edit { prefs ->
            val current = decodeServers(prefs)
            val issues = ServerValidator.validate(
                ip = ip,
                port = port,
                name = name,
                hostname = hostname,
                existingNames = current.filter { it.id != id }.map { it.name },
            )
            if (issues.any { it.severity == IssueSeverity.ERROR }) {
                throw IllegalArgumentException(issues.toString())
            }
            val updated = current.map {
                if (it.id == id) it.copy(
                    name = name,
                    ip = ip,
                    port = port,
                    token = token,
                    hostname = hostname?.takeIf { h -> h.isNotBlank() },
                    useTls = if (hostname.isNullOrBlank()) it.useTls else true,
                ) else it
            }
            prefs[keyServersJson] = json.encodeToString(updated)
            val active = resolveActive(prefs, updated)
            if (active?.id == id) prefs.mirrorLegacy(active)
        }
    }.map { }
}
