package com.Obscrum.pchwmonitor.ui

import android.content.Context
import com.Obscrum.pchwmonitor.R
import com.Obscrum.pchwmonitor.data.AppError

data class ErrorText(val resId: Int, val args: List<Any> = emptyList())

fun AppError.toErrorText(): ErrorText = when (this) {
    AppError.TokenRequired -> ErrorText(R.string.error_token_required)
    AppError.ConnectFailed -> ErrorText(R.string.error_connect_failed)
    AppError.SocketFailure -> ErrorText(R.string.error_socket_failure)
    AppError.AddressInvalid -> ErrorText(R.string.error_address_invalid)
    AppError.PlaintextToken -> ErrorText(R.string.error_plaintext_token)
    AppError.HistoryWriteFailed -> ErrorText(R.string.error_history_write_failed)
    AppError.DataUnavailable -> ErrorText(R.string.error_data_unavailable)
    is AppError.UnknownMessageType -> ErrorText(R.string.error_unknown_message_type, listOf(type))
    is AppError.ServerMessage -> ErrorText(R.string.error_raw, listOf(text))
}

fun Context.resolveError(error: AppError): String {
    val text = error.toErrorText()
    return getString(text.resId, *text.args.toTypedArray())
}
