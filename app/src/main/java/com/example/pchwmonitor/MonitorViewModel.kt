package com.example.pchwmonitor

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pchwmonitor.data.AppSettings
import com.example.pchwmonitor.data.SettingsStore
import com.example.pchwmonitor.data.ThemeMode
import com.example.pchwmonitor.data.local.HistoryDb
import com.example.pchwmonitor.data.local.HistoryRepository
import com.example.pchwmonitor.data.local.HistorySample
import com.example.pchwmonitor.data.network.ConnectionState
import com.example.pchwmonitor.data.network.StatusParser
import com.example.pchwmonitor.data.network.WebSocketClient
import com.example.pchwmonitor.domain.model.SystemStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun saveSettings(ip: String, port: Int, theme: ThemeMode, language: String?) {
        viewModelScope.launch {
            settingsStore.setServerIp(ip)
            settingsStore.setServerPort(port)
            settingsStore.setTheme(theme)
            settingsStore.setLanguage(language)
        }
    }

    suspend fun historySamples(start: Long): List<HistorySample> = controller.historySamples(start)

    suspend fun setServerIp(ip: String) = settingsStore.setServerIp(ip)

    suspend fun setServerPort(port: Int) = settingsStore.setServerPort(port)

    suspend fun setTheme(theme: ThemeMode) = settingsStore.setTheme(theme)

    suspend fun setLanguage(language: String?) = settingsStore.setLanguage(language)
}
