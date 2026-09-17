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
import java.security.MessageDigest
import java.security.cert.X509Certificate
import android.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

class WebSocketClient(
    private val parser: StatusParser = StatusParser,
    private val okHttp: OkHttpClient = defaultClient(),
    private val trustedCertHash: String? = null,
) : WsClient {
    private val _messages = MutableSharedFlow<WsMessage>(extraBufferCapacity = 64)
    override val messages: SharedFlow<WsMessage> = _messages.asSharedFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _trustedClient: OkHttpClient? = if (trustedCertHash != null) buildTrustedClient(trustedCertHash) else null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val closedEvents = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null
    private var ws: WebSocket? = null
    private var closed = false
    private var pendingToken: String? = null

    override fun connect(url: String, token: String?) {
        if (job?.isActive == true) return
        pendingToken = token
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
            while (closedEvents.tryReceive().isSuccess) { /* discard */ }
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
                // drain
            }
            // Apply backoff after any disconnection to avoid tight retry loops
            // (e.g. auth failure → connect → fail → connect → fail …)
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
        }
    }

    private fun tryOpen(url: String): WebSocket? {
        return try {
            val client = _trustedClient ?: okHttp
            val request = Request.Builder().url(url).build()
            client.newWebSocket(request, listener())
        } catch (e: Exception) {
            _messages.tryEmit(WsMessage.ParseFailure(url, e.message ?: "connect failed"))
            null
        }
    }

    private fun listener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            pendingToken?.takeIf { it.isNotBlank() }?.let { token ->
                val escaped = token.replace("\\", "\\\\").replace("\"", "\\\"")
                webSocket.send("""{"type":"auth","token":"$escaped"}""")
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            _messages.tryEmit(parser.parse(text))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val peerCert = response?.handshake?.peerCertificates?.firstOrNull()
            when (t) {
                is SSLPeerUnverifiedException -> {
                    if (peerCert != null) {
                        val hash = computeSpkiPin(peerCert)
                        _messages.tryEmit(WsMessage.CertUntrusted(hash, null))
                    }
                }
                is SSLHandshakeException -> {
                    if (peerCert != null) {
                        val hash = computeSpkiPin(peerCert)
                        _messages.tryEmit(WsMessage.CertUntrusted(hash, null))
                    }
                }
            }
            _messages.tryEmit(WsMessage.ParseFailure("socket", t.message ?: "socket failure"))
            _connectionState.value = ConnectionState.DISCONNECTED
            closedEvents.trySend(Unit)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (code == 1008) {
                _messages.tryEmit(WsMessage.ParseFailure("auth", "Token required — check server console for token"))
            }
            _connectionState.value = ConnectionState.DISCONNECTED
            closedEvents.trySend(Unit)
        }
    }

    companion object {
        /**
         * Compute SPKI pin in the format OkHttp expects: sha256/<Base64-of-SPKI-SHA256>
         */
        fun computeSpkiPin(cert: java.security.cert.Certificate): String {
            val x509 = cert as X509Certificate
            val subjectPublicKeyInfo = x509.publicKey.encoded
            val spkiSha256 = MessageDigest.getInstance("SHA-256").digest(subjectPublicKeyInfo)
            return "sha256/" + Base64.encodeToString(spkiSha256, Base64.NO_WRAP)
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        /**
         * Build a client that trusts only a specific certificate identified by its
         * SPKI pin (sha256/<Base64>). Uses a custom TrustManager that validates
         * the server cert matches the pinned hash, instead of CertificatePinner
         * which doesn't work with self-signed certificates.
         */
        fun buildTrustedClient(spkiPin: String): OkHttpClient {
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    // Not a server — not needed
                }

                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    if (chain == null || chain.isEmpty()) {
                        throw SSLPeerUnverifiedException("No server certificate presented")
                    }
                    val serverCert = chain[0]
                    // Verify SPKI pin — this IS the trust decision for self-signed certs
                    val serverPin = computeSpkiPin(serverCert)
                    if (serverPin != spkiPin) {
                        throw SSLPeerUnverifiedException(
                            "Certificate SPKI pin mismatch: expected sha256/$spkiPin, got sha256/$serverPin"
                        )
                    }
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }

            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustManager), null)
            }

            return defaultClient().newBuilder()
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                .hostnameVerifier { _, _ -> true } // SPKI pin provides auth; skip hostname check
                .build()
        }
    }
}
