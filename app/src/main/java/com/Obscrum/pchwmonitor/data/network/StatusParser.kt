package com.Obscrum.pchwmonitor.data.network

import com.Obscrum.pchwmonitor.domain.model.SystemStatus
import com.Obscrum.pchwmonitor.domain.model.WelcomeInfo
import com.Obscrum.pchwmonitor.domain.model.WsMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object StatusParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): WsMessage {
        return try {
            val type = json.parseToJsonElement(raw)
                .jsonObject["type"]?.jsonPrimitive?.contentOrNull
            when (type) {
                "welcome" -> WsMessage.Welcome(json.decodeFromString(WelcomeInfo.serializer(), raw))
                "status" -> {
                    val status = json.decodeFromString(SystemStatus.serializer(), raw)
                    WsMessage.Status(status.copy(timestamp = status.timestamp * 1000L))
                }
                else -> WsMessage.ParseFailure(raw, "unknown message type: $type")
            }
        } catch (e: Exception) {
            WsMessage.ParseFailure(raw, e.message ?: e.javaClass.simpleName)
        }
    }
}
