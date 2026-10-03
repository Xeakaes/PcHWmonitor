package com.Obscrum.pchwmonitor.data

data class ConnectionKey(
    val servers: List<ServerConfig>,
    val activeServerId: String?,
    val backgroundConnectAll: Boolean,
)

fun AppSettings.toConnectionKey(): ConnectionKey =
    ConnectionKey(
        servers = servers,
        activeServerId = activeServerId,
        backgroundConnectAll = backgroundConnectAll,
    )

data class ConnectionPlan(
    val connect: Map<String, ServerConfig>,
    val disconnect: Set<String>,
    val dispose: Set<String>,
)

private fun ServerConfig.connSignature(): List<Any?> =
    listOf(ip, port, token, hostname, useTls, trustedCertHash)

fun planConnections(
    servers: List<ServerConfig>,
    activeId: String?,
    backgroundAll: Boolean,
    current: Map<String, ServerConfig>,
): ConnectionPlan {
    val serverIds = servers.map { it.id }.toSet()
    val desired: Set<String> = if (backgroundAll) {
        serverIds
    } else {
        activeId?.let { setOf(it) } ?: emptySet()
    }
    val desiredById = servers.associateBy { it.id }

    val dispose = current.keys - serverIds

    val disconnect = buildSet {
        for (id in current.keys) {
            if (id in dispose) continue
            if (id !in desired) {
                add(id)
            } else {
                val wanted = desiredById[id]
                if (wanted != null && current.getValue(id).connSignature() != wanted.connSignature()) {
                    add(id)
                }
            }
        }
    }

    val connect = desired.mapNotNull { id ->
        val wanted = desiredById[id] ?: return@mapNotNull null
        val now = current[id]
        if (now == null || now.connSignature() != wanted.connSignature()) id to wanted else null
    }.toMap()

    return ConnectionPlan(connect = connect, disconnect = disconnect, dispose = dispose)
}

fun planOnStop(current: Map<String, ServerConfig>, backgroundAll: Boolean): ConnectionPlan =
    if (backgroundAll) {
        ConnectionPlan(connect = emptyMap(), disconnect = emptySet(), dispose = emptySet())
    } else {
        ConnectionPlan(connect = emptyMap(), disconnect = current.keys, dispose = emptySet())
    }
