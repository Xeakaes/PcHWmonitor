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
    private val pcId: String = "default",
    private val recordIntervalMs: Long = 5_000L,
) {
    val connection: StateFlow<ConnectionState> = client.connectionState

    private val _status = MutableStateFlow<SystemStatus?>(null)
    val status: StateFlow<SystemStatus?> = _status.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _certHash = MutableStateFlow<String?>(null)
    val certHash: StateFlow<String?> = _certHash.asStateFlow()

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
                                history.record(message.status, pcId)
                                lastRecordedAt = now
                            }
                        } else {
                            _lastError.value = message.status.error ?: "data unavailable"
                        }
                    }
                    is WsMessage.Welcome -> Unit
                    is WsMessage.ParseFailure -> _lastError.value = message.reason
                    is WsMessage.CertUntrusted -> _certHash.value = message.certHash
                }
            }
        }
    }

    fun connect(ip: String, port: Int, token: String? = null, useTls: Boolean = false, hostname: String? = null) {
        // If hostname is set (e.g. Cloudflare tunnel), use it directly
        val target = if (!hostname.isNullOrBlank()) {
            hostname.trim()
        } else {
            if (!isAllowedHost(ip)) {
                _lastError.value = "server address must be a private IP or known hostname"
                return
            }
            ip
        }
        if (!useTls && token != null) {
            _lastError.value = "Warning: token sent in plaintext (ws://). Enable TLS for secure auth."
        }
        val scheme = if (useTls) "wss" else "ws"
        val url = if (!hostname.isNullOrBlank()) {
            "$scheme://$target/ws"
        } else {
            "$scheme://$target:$port/ws"
        }
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
        private fun isAllowedHost(host: String): Boolean {
            // Allow known hostnames (Cloudflare tunnels, localhost, etc.)
            val lower = host.lowercase()
            if (lower == "localhost" || lower.endsWith(".trycloudflare.com")) return true
            // Allow private IPs
            val parts = host.split('.').mapNotNull { it.toIntOrNull() }
            if (parts.size != 4 || parts.any { it !in 0..255 }) return false
            val a = parts[0]
            val b = parts[1]
            return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || a == 127
        }
    }

    suspend fun historySamples(start: Long) = history.history(start, pcId)
}
