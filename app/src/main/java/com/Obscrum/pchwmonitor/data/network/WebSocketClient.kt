package com.Obscrum.pchwmonitor.data.network

import com.Obscrum.pchwmonitor.domain.model.WsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

class WebSocketClient(
    private val parser: StatusParser = StatusParser,
    private val okHttp: OkHttpClient = defaultClient(),
) : WsClient {
    private val _messages = MutableSharedFlow<WsMessage>(extraBufferCapacity = 64)
    override val messages: SharedFlow<WsMessage> = _messages.asSharedFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val closedEvents = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null
    private var ws: WebSocket? = null
    private var closed = false

    override fun connect(url: String) {
        if (job?.isActive == true) return
        closed = false
        job = scope.launch { connectLoop(url) }
    }

    override fun disconnect() {
        closed = true
        job?.cancel()
        job = null
        ws?.close(1000, "user disconnect")
        ws = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private suspend fun connectLoop(url: String) {
        var backoffMs = 1000L
        while (!closed) {
            _connectionState.value = ConnectionState.CONNECTING
            val socket = tryOpen(url)
            if (socket == null) {
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                continue
            }
            ws = socket
            _connectionState.value = ConnectionState.CONNECTED
            backoffMs = 1000L
            closedEvents.receive()
            ws = null
            while (closedEvents.tryReceive().isSuccess) {
                closedEvents.tryReceive()
            }
        }
    }

    private fun tryOpen(url: String): WebSocket? {
        return try {
            val request = Request.Builder().url(url).build()
            okHttp.newWebSocket(request, listener())
        } catch (e: Exception) {
            _messages.tryEmit(WsMessage.ParseFailure(url, e.message ?: "connect failed"))
            null
        }
    }

    private fun listener() = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            _messages.tryEmit(parser.parse(text))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _messages.tryEmit(WsMessage.ParseFailure("socket", t.message ?: "socket failure"))
            _connectionState.value = ConnectionState.DISCONNECTED
            closedEvents.trySend(Unit)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _connectionState.value = ConnectionState.DISCONNECTED
            closedEvents.trySend(Unit)
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
