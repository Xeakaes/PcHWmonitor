package com.Obscrum.pchwmonitor.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.Obscrum.pchwmonitor.data.AppSettings
import com.Obscrum.pchwmonitor.data.FieldIssue
import com.Obscrum.pchwmonitor.data.IssueSeverity
import com.Obscrum.pchwmonitor.data.ServerConfig
import com.Obscrum.pchwmonitor.data.ServerValidator
import com.Obscrum.pchwmonitor.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

class ServerEditViewModel(
    private val store: SettingsStore,
    private val serverId: String?,
) : ViewModel() {

    val name = MutableStateFlow("")
    val ip = MutableStateFlow("")
    val port = MutableStateFlow("")
    val token = MutableStateFlow("")
    val hostname = MutableStateFlow("")

    private val _issues = MutableStateFlow<List<FieldIssue>>(emptyList())
    val issues: StateFlow<List<FieldIssue>> = _issues.asStateFlow()

    val isNew: Boolean = serverId == null

    private var prefilled = false

    init {
        viewModelScope.launch {
            store.settings.collect { settings ->
                if (prefilled) return@collect
                prefill(settings)
            }
        }
    }

    private fun prefill(settings: AppSettings) {
        prefilled = true
        if (isNew) {
            port.value = "8765"
            return
        }
        settings.servers.find { it.id == serverId }?.let { row ->
            name.value = row.name
            ip.value = row.ip
            port.value = row.port.toString()
            token.value = row.token.orEmpty()
            hostname.value = row.hostname.orEmpty()
        }
    }

    fun save(): Boolean = runBlocking {
        val portNumber = port.value.toIntOrNull()
        val trimmedName = name.value.trim()
        val trimmedIp = ip.value.trim()
        val trimmedToken = token.value.trim()
        val trimmedHostname = hostname.value.trim()
        val validated = ServerValidator.validate(
            ip = trimmedIp,
            port = portNumber,
            name = trimmedName,
            hostname = trimmedHostname,
            existingNames = store.settings.first().servers
                .filter { it.id != serverId }
                .map { it.name },
        )
        _issues.value = validated
        if (validated.any { it.severity == IssueSeverity.ERROR }) return@runBlocking false
        val portNumberOrBlocked = portNumber ?: return@runBlocking false
        val result = if (isNew) {
            store.addServer(
                ServerConfig(
                    id = UUID.randomUUID().toString(),
                    name = trimmedName,
                    ip = trimmedIp,
                    port = portNumberOrBlocked,
                    token = trimmedToken.ifBlank { null },
                    hostname = trimmedHostname.ifBlank { null },
                ),
            )
        } else {
            store.updateServerConnection(
                id = serverId!!,
                name = trimmedName,
                ip = trimmedIp,
                port = portNumberOrBlocked,
                token = trimmedToken.ifBlank { null },
                hostname = trimmedHostname.ifBlank { null },
            )
        }
        result.isSuccess
    }

    companion object {
        fun factory(store: SettingsStore, serverId: String?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ServerEditViewModel(store, serverId) as T
                }
            }
    }
}
