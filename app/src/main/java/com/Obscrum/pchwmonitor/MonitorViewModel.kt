package com.Obscrum.pchwmonitor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.Obscrum.pchwmonitor.data.AppSettings
import com.Obscrum.pchwmonitor.data.AppError
import com.Obscrum.pchwmonitor.data.ConnectionKey
import com.Obscrum.pchwmonitor.data.ConnectionPlan
import com.Obscrum.pchwmonitor.data.ServerConfig
import com.Obscrum.pchwmonitor.data.SettingsStore
import com.Obscrum.pchwmonitor.data.ThemeMode
import com.Obscrum.pchwmonitor.data.planConnections
import com.Obscrum.pchwmonitor.data.planOnStop
import com.Obscrum.pchwmonitor.data.toConnectionKey
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
import com.Obscrum.pchwmonitor.util.QrMatch
import com.Obscrum.pchwmonitor.util.QrPayload
import com.Obscrum.pchwmonitor.util.matchServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MonitorViewModel(app: Application) : AndroidViewModel(app) {

    val settingsStore: SettingsStore = SettingsStore(
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

    val glassmorphism: StateFlow<Boolean> = settingsStore.settings
        .map { it.glassmorphismEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Cert trust dialog state
    private val _pendingCertHash = MutableStateFlow<String?>(null)
    val pendingCertHash: StateFlow<String?> = _pendingCertHash.asStateFlow()

    // Track collect jobs to cancel on server switch
    private val activeCollectJobs = mutableListOf<Job>()

    // Multi-PC state
    private val _controllers = mutableMapOf<String, MonitorController>()
    private val connectedConfigs = mutableMapOf<String, ServerConfig>()
    private val _activeServerId = MutableStateFlow<String?>(null)
    val activeServerId: StateFlow<String?> = _activeServerId.asStateFlow()

    private val _activeStatus = MutableStateFlow<SystemStatus?>(null)
    val activeStatus: StateFlow<SystemStatus?> = _activeStatus.asStateFlow()

    private val _activeConnection = MutableStateFlow<ConnectionState?>(ConnectionState.DISCONNECTED)
    val activeConnection: StateFlow<ConnectionState?> = _activeConnection.asStateFlow()

    private val _lastError = MutableStateFlow<AppError?>(null)
    val lastError: StateFlow<AppError?> = _lastError.asStateFlow()

    val discovery = DiscoveryService(viewModelScope, app)

    init {
        viewModelScope.launch {
            settings.map { it.toConnectionKey() }.distinctUntilChanged().collect { key ->
                applyPlan(
                    planConnections(
                        servers = key.servers,
                        activeId = key.activeServerId,
                        backgroundAll = key.backgroundConnectAll,
                        current = connectedConfigs,
                    ),
                )
                repairActive(key)
            }
        }
        viewModelScope.launch {
            settings.collect { s ->
                // Load custom background if enabled
                if (s.customBackgroundEnabled && s.customBackgroundUri != null) {
                    loadCustomBackground()
                }
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            BackgroundConnectionHandler(
                onForeground = {
                    applyPlan(
                        planConnections(
                            servers = settings.value.servers,
                            activeId = _activeServerId.value,
                            backgroundAll = settings.value.backgroundConnectAll,
                            current = connectedConfigs,
                        ),
                    )
                },
                onBackground = {
                    applyPlan(planOnStop(connectedConfigs, settings.value.backgroundConnectAll))
                },
            ),
        )
    }

    private fun applyPlan(plan: ConnectionPlan) {
        plan.dispose.forEach { id ->
            _controllers.remove(id)?.dispose()
            connectedConfigs.remove(id)
        }
        plan.disconnect.forEach { id ->
            _controllers[id]?.disconnect()
            connectedConfigs.remove(id)
        }
        plan.connect.forEach { (id, cfg) ->
            ensureController(id, cfg).connect(
                cfg.ip,
                cfg.port,
                cfg.token,
                cfg.useTls,
                cfg.hostname,
            )
            connectedConfigs[id] = cfg
        }
    }

    private fun ensureController(id: String, cfg: ServerConfig): MonitorController =
        _controllers.getOrPut(id) {
            val client = WebSocketClient(parser = StatusParser, trustedCertHash = cfg.trustedCertHash)
            val hist = HistoryRepository(HistoryDb.get(getApplication()).historyDao())
            MonitorController(client = client, history = hist, scope = viewModelScope, pcId = id)
                .also { it.start() }
        }

    private fun repairActive(key: ConnectionKey) {
        val active = _activeServerId.value
        if (active == null || key.servers.none { it.id == active }) {
            val resolved = key.activeServerId ?: key.servers.firstOrNull()?.id
            _activeServerId.value = resolved
            if (resolved == null) {
                _activeStatus.value = null
                _activeConnection.value = null
                _lastError.value = null
            }
        }
        val activeId = _activeServerId.value
        val serverName = key.servers.find { it.id == activeId }?.name
        MonitorNotificationService.updateServerName(serverName)
        collectFromActiveController()
    }

    private fun collectFromActiveController() {
        activeCollectJobs.forEach { it.cancel() }
        activeCollectJobs.clear()
        val activeId = _activeServerId.value
        if (activeId == null) {
            _activeStatus.value = null
            _activeConnection.value = null
            _lastError.value = null
            return
        }
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
        _controllers.remove(id)?.dispose()
        connectedConfigs.remove(id)
        viewModelScope.launch { settingsStore.removeServer(id) }
    }

    fun connectViaQr(payload: QrPayload.Result) {
        when (val match = matchServer(settings.value.servers, payload)) {
            is QrMatch.Existing -> setActiveServer(match.serverId)
            is QrMatch.New -> addServer(match.config)
        }
    }

    fun trustCert(certHash: String) {
        val activeId = _activeServerId.value ?: return
        viewModelScope.launch {
            settingsStore.updateServerCertHash(activeId, certHash)
            _pendingCertHash.value = null
            // Reconnect with trusted cert
            val cfg = settings.value.servers.find { it.id == activeId } ?: return@launch
            _controllers.remove(activeId)?.dispose()
            val trusted = cfg.copy(trustedCertHash = certHash, useTls = true)
            val client = WebSocketClient(parser = StatusParser, trustedCertHash = trusted.trustedCertHash)
            val hist = HistoryRepository(HistoryDb.get(getApplication()).historyDao())
            val ctrl = MonitorController(client = client, history = hist, scope = viewModelScope, pcId = activeId)
            ctrl.start()
            _controllers[activeId] = ctrl
            ctrl.connect(trusted.ip, trusted.port, trusted.token, trusted.useTls, trusted.hostname)
            connectedConfigs[activeId] = trusted
            collectFromActiveController()
        }
    }

    fun dismissCertDialog() {
        _pendingCertHash.value = null
    }

    fun disconnect() = _controllers.values.forEach { it.disconnect() }

    fun savePreferences(theme: ThemeMode, language: String?, chartWindowSeconds: Int, themePaletteId: String) {
        viewModelScope.launch {
            settingsStore.savePreferences(theme, language, chartWindowSeconds, themePaletteId)
        }
    }

    suspend fun historySamples(start: Long): List<HistorySample> {
        val activeId = _activeServerId.value ?: return emptyList()
        return _controllers[activeId]?.historySamples(start) ?: emptyList()
    }

    suspend fun setServerIp(ip: String) = settingsStore.setServerIp(ip)

    suspend fun setServerPort(port: Int) = settingsStore.setServerPort(port)

    fun setTheme(theme: ThemeMode) {
        viewModelScope.launch { settingsStore.setTheme(theme) }
    }

    fun setLanguage(language: String?) {
        viewModelScope.launch { settingsStore.setLanguage(language) }
    }

    fun setChartWindowSeconds(seconds: Int) {
        viewModelScope.launch { settingsStore.setChartWindowSeconds(seconds) }
    }

    fun setThemePalette(id: String) {
        viewModelScope.launch { settingsStore.setThemePalette(id) }
    }

    fun setBackgroundConnectAll(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setBackgroundConnectAll(enabled) }
    }

    fun setDashboardLayout(layout: DashboardLayout) {
        viewModelScope.launch { settingsStore.setDashboardLayout(layout) }
    }

    // Custom Background methods
    fun setCustomBackgroundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setCustomBackgroundEnabled(enabled)
            if (enabled) {
                loadCustomBackground()
            } else {
                _customBackgroundBitmap.value = null
            }
        }
    }

    fun setGlassmorphismEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setGlassmorphismEnabled(enabled) }
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
