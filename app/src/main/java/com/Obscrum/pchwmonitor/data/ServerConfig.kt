package com.Obscrum.pchwmonitor.data

import kotlinx.serialization.Serializable

@Serializable
data class ServerConfig(
    val id: String,
    val name: String,
    val ip: String,
    val port: Int = 8765,
    val token: String? = null,
    val trustedCertHash: String? = null,
    val useTls: Boolean = false,
)
