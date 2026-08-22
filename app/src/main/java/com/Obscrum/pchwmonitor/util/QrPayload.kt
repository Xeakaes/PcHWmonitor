package com.Obscrum.pchwmonitor.util

/**
 * Parser for the QR payload emitted by the Windows server tray:
 *   pwch://connect?ip=192.168.1.50&port=8765&token=<urlsafe-token>
 */
object QrPayload {
    private const val SCHEME = "pchw://connect?"

    /** Returns (ip, port, token) when [text] is a valid connection QR payload, else null. */
    fun parse(text: String): Triple<String, Int, String>? {
        if (!text.startsWith(SCHEME)) return null
        val params = text.removePrefix(SCHEME)
            .split('&')
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null else part.take(idx) to part.substring(idx + 1)
            }
            .toMap()
        val ip = params["ip"]?.takeIf { it.isNotBlank() } ?: return null
        val port = params["port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val token = params["token"]?.takeIf { it.isNotBlank() } ?: return null
        return Triple(ip, port, token)
    }
}
