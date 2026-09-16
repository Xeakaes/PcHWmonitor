package com.Obscrum.pchwmonitor.util

/**
 * Parser for the QR payload emitted by the Windows server tray:
 *   pchw://connect?ip=192.168.1.50&port=8765&token=<urlsafe-token>
 *   pchw://connect?hostname=my-tunnel.trycloudflare.com&token=<urlsafe-token>
 */
object QrPayload {
    private const val SCHEME = "pchw://connect?"

    data class Result(val ip: String, val port: Int, val token: String, val hostname: String? = null)

    /** Returns a Result when [text] is a valid connection QR payload, else null. */
    fun parse(text: String): Result? {
        if (!text.startsWith(SCHEME)) return null
        val params = text.removePrefix(SCHEME)
            .split('&')
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null else part.take(idx) to part.substring(idx + 1)
            }
            .toMap()
        val token = params["token"]?.takeIf { it.isNotBlank() } ?: return null
        val hostname = params["hostname"]?.takeIf { it.isNotBlank() }
        if (hostname != null) {
            return Result(ip = "", port = 8765, token = token, hostname = hostname)
        }
        val ip = params["ip"]?.takeIf { it.isNotBlank() } ?: return null
        val port = params["port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        return Result(ip = ip, port = port, token = token)
    }
}
