package com.Obscrum.pchwmonitor.util

import com.Obscrum.pchwmonitor.data.ServerConfig
import java.util.UUID

sealed interface QrMatch {
    data class Existing(val serverId: String) : QrMatch
    data class New(val config: ServerConfig) : QrMatch
}

fun matchServer(servers: List<ServerConfig>, payload: QrPayload.Result): QrMatch {
    val payloadHostname = payload.hostname?.takeIf { it.isNotBlank() }
    val existing = if (payloadHostname != null) {
        servers.firstOrNull { server ->
            !server.hostname.isNullOrBlank() && server.hostname == payloadHostname
        }
    } else {
        servers.firstOrNull { server ->
            server.ip == payload.ip && server.port == payload.port
        }
    }
    if (existing != null) return QrMatch.Existing(existing.id)
    return QrMatch.New(
        ServerConfig(
            id = UUID.randomUUID().toString(),
            name = payload.hostname ?: payload.ip,
            ip = payload.ip,
            port = payload.port,
            token = payload.token,
            hostname = payload.hostname,
            useTls = payload.hostname != null,
        )
    )
}
