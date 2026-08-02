package com.example.pchwmonitor.data.network

import com.example.pchwmonitor.domain.model.WsMessage
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface WsClient {
    val messages: SharedFlow<WsMessage>
    val connectionState: StateFlow<ConnectionState>
    fun connect(url: String)
    fun disconnect()
}
