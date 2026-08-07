package com.Obscrum.pchwmonitor

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Obscrum.pchwmonitor.data.AppSettings
import com.Obscrum.pchwmonitor.data.SettingsStore
import com.Obscrum.pchwmonitor.data.ThemeMode
import com.Obscrum.pchwmonitor.data.local.HistoryDb
import com.Obscrum.pchwmonitor.data.local.HistoryRepository
import com.Obscrum.pchwmonitor.data.local.HistorySample
import com.Obscrum.pchwmonitor.data.network.ConnectionState
import com.Obscrum.pchwmonitor.data.network.StatusParser
import com.Obscrum.pchwmonitor.data.network.WebSocketClient
import com.Obscrum.pchwmonitor.domain.model.SystemStatus
import com.Obscrum.pchwmonitor.ui.dashboard.DashboardLayout
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MonitorViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsStore: SettingsStore = SettingsStore(
        PreferenceDataStoreFactory.create(
            scope = viewModelScope,
            produceFile = { File(app.filesDir, "settings.preferences_pb") },
        ),
    )
    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val chartWindowSeconds: StateFlow<Int> = settingsStore.settings
        .map { it.chartWindowSeconds }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings().chartWindowSeconds)
    val themePaletteId: StateFlow<String> = settingsStore.settings
        .map { it.themePaletteId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings().themePaletteId)
    val dashboardLayout: StateFlow<DashboardLayout> = settingsStore.settings
        .map { it.dashboardLayout }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings().dashboardLayout)

    private val controller = MonitorController(
        client = WebSocketClient(parser = StatusParser),
        history = HistoryRepository(HistoryDb.get(app).historyDao()),
        scope = viewModelScope,
    )

    val connection: StateFlow<ConnectionState> = controller.connection
    val status: StateFlow<SystemStatus?> = controller.status
    val lastError: StateFlow<String?> = controller.lastError

    init {
        controller.start()
        viewModelScope.launch {
            settings.collect { s -> controller.connect(s.serverIp, s.serverPort) }
        }
    }

    fun disconnect() = controller.disconnect()

    fun saveSettings(ip: String, port: Int, theme: ThemeMode, language: String?,
                     chartWindowSeconds: Int) {
        viewModelScope.launch {
            settingsStore.setServerIp(ip)
            settingsStore.setServerPort(port)
            settingsStore.setTheme(theme)
            settingsStore.setLanguage(language)
            settingsStore.setChartWindowSeconds(chartWindowSeconds)
        }
    }

    suspend fun historySamples(start: Long): List<HistorySample> = controller.historySamples(start)

    suspend fun setServerIp(ip: String) = settingsStore.setServerIp(ip)

    suspend fun setServerPort(port: Int) = settingsStore.setServerPort(port)

    suspend fun setTheme(theme: ThemeMode) = settingsStore.setTheme(theme)

    suspend fun setLanguage(language: String?) = settingsStore.setLanguage(language)

    fun setThemePalette(id: String) {
        viewModelScope.launch { settingsStore.setThemePalette(id) }
    }

    fun setDashboardLayout(layout: DashboardLayout) {
        viewModelScope.launch { settingsStore.setDashboardLayout(layout) }
    }
}
