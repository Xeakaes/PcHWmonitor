package com.Obscrum.pchwmonitor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.Obscrum.pchwmonitor.data.AppSettings
import com.Obscrum.pchwmonitor.data.ServerConfig
import com.Obscrum.pchwmonitor.data.SettingsStore
import com.Obscrum.pchwmonitor.data.ThemeMode
import com.Obscrum.pchwmonitor.data.local.HistoryDb
import com.Obscrum.pchwmonitor.data.local.HistoryRepository
import com.Obscrum.pchwmonitor.data.local.HistorySample
import com.Obscrum.pchwmonitor.data.network.ConnectionState
import com.Obscrum.pchwmonitor.data.network.DiscoveryService
import com.Obscrum.pchwmonitor.data.network.StatusParser
import com.Obscrum.pchwmonitor.data.network.WebSocketClient
import com.Obscrum.pchwmonitor.domain.model.SystemStatus
import com.Obscrum.pchwmonitor.service.MonitorNotificationService
import com.Obscrum.pchwmonitor.ui.dashboard.DashboardLayout
import com.Obscrum.pchwmonitor.ui.theme.CustomBackgroundManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val servers: StateFlow<List<ServerConfig>> = settingsStore.settings
        .map { it.servers }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Custom Background state
    private val _customBackgroundBitmap = MutableStateFlow<Bitmap?>(null)
    val customBackgroundBitmap: StateFlow<Bitmap?> = _customBackgroundBitmap.asStateFlow()

    private val _glassmorphism = MutableStateFlow(false)
    val glassmorphism: StateFlow<Boolean> = _glassmorphism.asStateFlow()

    // Cert trust dialog state
    private val _pendingCertHash = MutableStateFlow<String?>(null)
    val pendingCertHash: StateFlow<String?> = _pendingCertHash.asStateFlow()

    // Track collect jobs to cancel on server switch
    private val activeCollectJobs = mutableListOf<Job>()

    // Multi-PC state
    private val _controllers = mutableMapOf<String, MonitorController>()
    private val _activeServerId = MutableStateFlow<String?>(null)
    val activeServerId: StateFlow<String?> = _activeServerId.asStateFlow()

    private val _activeStatus = MutableStateFlow<SystemStatus?>(null)
    val activeStatus: StateFlow<SystemStatus?> = _activeStatus.asStateFlow()

    private val _activeConnection = MutableStateFlow(ConnectionState.DISCONNECTED)
    val activeConnection: StateFlow<ConnectionState> = _activeConnection.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    val discovery = DiscoveryService(viewModelScope, app)

    init {
        viewModelScope.launch {
            settings.collect { s ->
                syncControllers(s)
                // Load custom background if enabled
                if (s.customBackgroundEnabled && s.customBackgroundUri != null) {
                    loadCustomBackground()
                    _glassmorphism.value = true
                }
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            BackgroundConnectionHandler(
                serversProvider = { settings.value.servers },
                controllers = _controllers,
            ),
        )
    }

    private fun syncControllers(settings: AppSettings) {
        val desired = settings.servers.associateBy { it.id }
        // Remove controllers for deleted servers
        _controllers.keys.retainAll(desired.keys)
        // Add controllers for new servers
        desired.forEach { (id, cfg) ->
            if (id !in _controllers) {
                val client = WebSocketClient(parser = StatusParser, trustedCertHash = cfg.trustedCertHash)
                val hist = HistoryRepository(HistoryDb.get(getApplication()).historyDao())
                val ctrl = MonitorController(client = client, history = hist, scope = viewModelScope, pcId = id)
                ctrl.start()
                _controllers[id] = ctrl
            }
            _controllers[id]?.connect(cfg.ip, cfg.port, cfg.token, cfg.useTls)
        }
        // Set active if none or invalid
        if (_activeServerId.value == null || _activeServerId.value !in _controllers) {
            _activeServerId.value = settings.activeServerId ?: settings.servers.firstOrNull()?.id
        }
        // Update notification with active server name
        val activeId = _activeServerId.value
        if (activeId != null) {
            val serverName = settings.servers.find { it.id == activeId }?.name
            MonitorNotificationService.updateServerName(serverName)
        }
        // Re-collect from active controller (cancel old jobs first)
        collectFromActiveController()
    }

    private fun collectFromActiveController() {
        activeCollectJobs.forEach { it.cancel() }
        activeCollectJobs.clear()
        val activeId = _activeServerId.value ?: return
        val ctrl = _controllers[activeId] ?: return
        activeCollectJobs += viewModelScope.launch { ctrl.status.collect { _activeStatus.value = it } }
        activeCollectJobs += viewModelScope.launch { ctrl.connection.collect { _activeConnection.value = it } }
        activeCollectJobs += viewModelScope.launch { ctrl.lastError.collect { _lastError.value = it } }
        activeCollectJobs += viewModelScope.launch {
            ctrl.certHash.collect { hash ->
                if (hash != null) _pendingCertHash.value = hash
            }
        }
    }

    fun setActiveServer(id: String) {
        _activeServerId.value = id
        viewModelScope.launch { settingsStore.setActiveServerId(id) }
        val serverName = settings.value.servers.find { it.id == id }?.name
        MonitorNotificationService.updateServerName(serverName)
        collectFromActiveController()
    }

    fun addServer(server: ServerConfig) {
        viewModelScope.launch { settingsStore.addServer(server) }
    }

    fun removeServer(id: String) {
        viewModelScope.launch {
            _controllers[id]?.disconnect()
            _controllers.remove(id)
            settingsStore.removeServer(id)
        }
    }

    fun trustCert(certHash: String) {
        val activeId = _activeServerId.value ?: return
        viewModelScope.launch {
            settingsStore.updateServerCertHash(activeId, certHash)
            _pendingCertHash.value = null
            // Reconnect with trusted cert
            val cfg = settings.value.servers.find { it.id == activeId } ?: return@launch
            _controllers[activeId]?.disconnect()
            val client = WebSocketClient(parser = StatusParser, trustedCertHash = certHash)
            val hist = HistoryRepository(HistoryDb.get(getApplication()).historyDao())
            val ctrl = MonitorController(client = client, history = hist, scope = viewModelScope, pcId = activeId)
            ctrl.start()
            _controllers[activeId] = ctrl
            ctrl.connect(cfg.ip, cfg.port, cfg.token, true)
        }
    }

    fun dismissCertDialog() {
        _pendingCertHash.value = null
    }

    fun disconnect() = _controllers.values.forEach { it.disconnect() }

    fun saveSettings(ip: String, port: Int, authToken: String?, theme: ThemeMode, language: String?,
                     chartWindowSeconds: Int) {
        viewModelScope.launch {
            settingsStore.setServerIp(ip)
            settingsStore.setServerPort(port)
            settingsStore.setAuthToken(authToken)
            settingsStore.setTheme(theme)
            settingsStore.setLanguage(language)
            settingsStore.setChartWindowSeconds(chartWindowSeconds)
        }
    }

    suspend fun historySamples(start: Long): List<HistorySample> {
        val activeId = _activeServerId.value ?: return emptyList()
        return _controllers[activeId]?.historySamples(start) ?: emptyList()
    }

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

    // Custom Background methods
    fun setCustomBackgroundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setCustomBackgroundEnabled(enabled)
            _glassmorphism.value = enabled
            if (enabled) {
                loadCustomBackground()
            } else {
                _customBackgroundBitmap.value = null
            }
        }
    }

    fun setGlassmorphismEnabled(enabled: Boolean) {
        _glassmorphism.value = enabled
    }

    fun pickCustomBackground(uriString: String) {
        viewModelScope.launch {
            val uri = Uri.parse(uriString)
            val context = getApplication<Application>()
            val saved = CustomBackgroundManager.saveBackground(context, uri)
            if (saved) {
                settingsStore.setCustomBackgroundUri(uriString)
                loadCustomBackground()
            }
        }
    }

    fun removeCustomBackground() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            CustomBackgroundManager.removeBackground(context)
            settingsStore.setCustomBackgroundUri(null)
            _customBackgroundBitmap.value = null
        }
    }

    private fun loadCustomBackground() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val bitmap = CustomBackgroundManager.loadBitmap(context)
            _customBackgroundBitmap.value = bitmap
        }
    }
}
