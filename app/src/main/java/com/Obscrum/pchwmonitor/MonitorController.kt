package com.Obscrum.pchwmonitor

import com.Obscrum.pchwmonitor.data.local.HistoryStore
import com.Obscrum.pchwmonitor.data.network.ConnectionState
import com.Obscrum.pchwmonitor.data.network.WsClient
import com.Obscrum.pchwmonitor.domain.model.SystemStatus
import com.Obscrum.pchwmonitor.domain.model.WsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MonitorController(
    private val client: WsClient,
    private val history: HistoryStore,
    private val scope: CoroutineScope,
    private val recordIntervalMs: Long = 5_000L,
) {
    val connection: StateFlow<ConnectionState> = client.connectionState

    private val _status = MutableStateFlow<SystemStatus?>(null)
    val status: StateFlow<SystemStatus?> = _status.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var currentUrl: String? = null
    private var currentToken: String? = null
    private var lastRecordedAt = Long.MIN_VALUE

    fun start() {
        scope.launch {
            client.messages.collect { message ->
                when (message) {
                    is WsMessage.Status -> {
                        _status.value = message.status
                        if (message.status.available) {
                            _lastError.value = null
                            val now = message.status.timestamp
                            val firstRecord = lastRecordedAt == Long.MIN_VALUE
                            if (firstRecord || now - lastRecordedAt >= recordIntervalMs) {
                                history.record(message.status)
                                lastRecordedAt = now
                            }
                        } else {
                            _lastError.value = message.status.error ?: "data unavailable"
                        }
                    }
                    is WsMessage.Welcome -> Unit
                    is WsMessage.ParseFailure -> _lastError.value = message.reason
                }
            }
        }
    }

    fun connect(ip: String, port: Int, token: String? = null) {
        if (!isPrivateIp(ip)) {
            _lastError.value = "server IP must be in a private range"
            return
        }
        val url = "ws://$ip:$port/ws"
        if (url == currentUrl && token == currentToken) return
        currentUrl = url
        currentToken = token
        client.disconnect()
        client.connect(url, token)
    }

    fun disconnect() {
        currentUrl = null
        currentToken = null
        client.disconnect()
    }

    companion object {
        private fun isPrivateIp(ip: String): Boolean {
            val parts = ip.split('.').mapNotNull { it.toIntOrNull() }
            if (parts.size != 4 || parts.any { it !in 0..255 }) return false
            val a = parts[0]
            val b = parts[1]
            return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || a == 127
        }
    }

    suspend fun historySamples(start: Long) = history.history(start)
}
