package com.Obscrum.pchwmonitor

import com.Obscrum.pchwmonitor.util.QrPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrPayloadTest {

    @Test
    fun parse_validPayload_returnsFields() {
        val result = QrPayload.parse("pchw://connect?ip=192.168.1.50&port=8765&token=abc_123")
        assertEquals(Triple("192.168.1.50", 8765, "abc_123"), result)
    }

    @Test
    fun parse_wrongScheme_returnsNull() {
        assertNull(QrPayload.parse("https://connect?ip=1.2.3.4&port=8765&token=t"))
    }

    @Test
    fun parse_missingToken_returnsNull() {
        assertNull(QrPayload.parse("pchw://connect?ip=10.0.0.2&port=8765"))
    }

    @Test
    fun parse_invalidPort_returnsNull() {
        assertNull(QrPayload.parse("pchw://connect?ip=10.0.0.2&port=99999&token=t"))
        assertNull(QrPayload.parse("pchw://connect?ip=10.0.0.2&port=abc&token=t"))
    }

    @Test
    fun parse_blankIp_returnsNull() {
        assertNull(QrPayload.parse("pchw://connect?ip=&port=8765&token=t"))
    }
}
