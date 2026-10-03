package com.Obscrum.pchwmonitor.data

sealed interface AppError {
    object TokenRequired : AppError
    object ConnectFailed : AppError
    object SocketFailure : AppError
    object AddressInvalid : AppError
    object PlaintextToken : AppError
    object HistoryWriteFailed : AppError
    object DataUnavailable : AppError
    data class UnknownMessageType(val type: String) : AppError
    data class ServerMessage(val text: String) : AppError
}
