package com.Obscrum.pchwmonitor

import com.Obscrum.pchwmonitor.data.network.ConnectionState
import com.Obscrum.pchwmonitor.data.network.WebSocketClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64

class WebSocketClientTest {
    private var server: ServerSocket? = null
    private var client: WebSocketClient? = null

    @After
    fun tearDown() {
        client?.disconnect()
        client = null
        try {
            server?.close()
        } catch (_: Exception) {
        }
        server = null
    }

    @Test
    fun `connectionState stays CONNECTING until handshake completes`() {
        val srv = ServerSocket(0)
        server = srv
        val accepted = mutableListOf<Socket>()
        Thread {
            try {
                while (!srv.isClosed) {
                    val s = srv.accept()
                    synchronized(accepted) { accepted.add(s) }
                }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true; start() }

        val c = WebSocketClient()
        client = c
        c.connect("ws://127.0.0.1:${srv.localPort}/", null)

        var sawConnected = false
        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline) {
            if (c.connectionState.value == ConnectionState.CONNECTED) {
                sawConnected = true
                break
            }
            Thread.sleep(50)
        }
        assertFalse(
            "Must not report CONNECTED before the WS handshake completes",
            sawConnected,
        )
        assertEquals(ConnectionState.CONNECTING, c.connectionState.value)
        synchronized(accepted) { accepted.forEach { it.close() } }
    }

    @Test
    fun `connectionState becomes CONNECTED after successful handshake`() {
        val srv = ServerSocket(0)
        server = srv
        Thread {
            try {
                val s = srv.accept()
                val inp = BufferedReader(InputStreamReader(s.getInputStream()))
                var key = ""
                while (true) {
                    val line = inp.readLine() ?: break
                    if (line.startsWith("Sec-WebSocket-Key:", ignoreCase = true)) {
                        key = line.substringAfter(":").trim()
                    }
                    if (line.isEmpty()) break
                }
                val guid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
                val sha = MessageDigest.getInstance("SHA-1").digest((key + guid).toByteArray())
                val accept = Base64.getEncoder().encodeToString(sha)
                val resp = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: $accept\r\n\r\n"
                s.getOutputStream().write(resp.toByteArray())
                s.getOutputStream().flush()
                while (!srv.isClosed) Thread.sleep(200)
                s.close()
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true; start() }

        val c = WebSocketClient()
        client = c
        c.connect("ws://127.0.0.1:${srv.localPort}/", null)

        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline &&
            c.connectionState.value != ConnectionState.CONNECTED
        ) {
            Thread.sleep(50)
        }
        assertEquals(
            "A completed handshake must reach CONNECTED",
            ConnectionState.CONNECTED,
            c.connectionState.value,
        )
    }
}
