package com.Obscrum.pchwmonitor

import com.Obscrum.pchwmonitor.data.AppError
import com.Obscrum.pchwmonitor.ui.toErrorText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ErrorMapperTest {

    @Test
    fun everyAppError_mapsToAResource_withArgsIntact() {
        val objects = listOf(
            AppError.TokenRequired,
            AppError.ConnectFailed,
            AppError.SocketFailure,
            AppError.AddressInvalid,
            AppError.PlaintextToken,
            AppError.HistoryWriteFailed,
            AppError.DataUnavailable,
        )
        objects.forEach { appError ->
            val text = appError.toErrorText()
            assertTrue("resId must be non-zero for $appError", text.resId != 0)
            assertTrue("args must be empty for $appError", text.args.isEmpty())
        }

        val unknown = AppError.UnknownMessageType("x").toErrorText()
        assertEquals(R.string.error_unknown_message_type, unknown.resId)
        assertEquals(listOf("x"), unknown.args)

        val raw = AppError.ServerMessage("boom").toErrorText()
        assertEquals(R.string.error_raw, raw.resId)
        assertEquals(listOf("boom"), raw.args)
    }

    @Test
    fun mappedKeys_existInBaseStrings() {
        val xml = File(findResDir(), "values/strings.xml").readText()
        val keys = listOf(
            "error_token_required",
            "error_connect_failed",
            "error_socket_failure",
            "error_address_invalid",
            "error_plaintext_token",
            "error_history_write_failed",
            "error_data_unavailable",
            "error_unknown_message_type",
            "error_raw",
        )
        keys.forEach { key ->
            assertTrue("missing string key in base strings: $key", xml.contains("name=\"$key\""))
        }
    }

    private fun findResDir(): File {
        val candidates = listOf(File("src/main/res"), File("app/src/main/res"))
        return candidates.firstOrNull { File(it, "values/strings.xml").exists() }
            ?: error("values/strings.xml not found relative to ${System.getProperty("user.dir")}")
    }
}
