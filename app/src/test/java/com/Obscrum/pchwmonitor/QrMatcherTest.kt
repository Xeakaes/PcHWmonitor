package com.Obscrum.pchwmonitor

import com.Obscrum.pchwmonitor.data.ServerConfig
import com.Obscrum.pchwmonitor.util.QrMatch
import com.Obscrum.pchwmonitor.util.QrPayload
import com.Obscrum.pchwmonitor.util.matchServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrMatcherTest {

    private fun server(
        id: String,
        ip: String = "",
        port: Int = 8765,
        hostname: String? = null,
    ): ServerConfig = ServerConfig(id = id, name = id, ip = ip, port = port, hostname = hostname)

    @Test
    fun hostnamePayload_matchesByHostname_evenIfIpsDiffer() {
        val s = server("s1", ip = "1.2.3.4", hostname = "t.trycloudflare.com")
        val payload = QrPayload.Result(ip = "", port = 8765, token = "tok", hostname = "t.trycloudflare.com")
        assertEquals(QrMatch.Existing(s.id), matchServer(listOf(s), payload))
    }

    @Test
    fun ipPayload_matchesByIpPort_andIgnoresHostnamelessCandidatesWithDifferentAddr() {
        val s1 = server("s1", ip = "1.2.3.4", port = 8765)
        val s2 = server("s2", ip = "5.6.7.8", port = 8765)
        val payload = QrPayload.Result(ip = "5.6.7.8", port = 8765, token = "t")
        assertEquals(QrMatch.Existing(s2.id), matchServer(listOf(s1, s2), payload))
    }

    @Test
    fun noMatch_createsNew_withHostnameImpliesTls() {
        val s = server("s1", ip = "1.2.3.4")
        val payload = QrPayload.Result(ip = "", port = 8765, token = "t", hostname = "new.tunnel.com")
        val match = matchServer(listOf(s), payload)
        assertTrue("expected New, got $match", match is QrMatch.New)
        val config = (match as QrMatch.New).config
        assertEquals("new.tunnel.com", config.hostname)
        assertEquals(true, config.useTls)
        assertEquals("", config.ip)
        assertEquals("t", config.token)
        assertEquals("new.tunnel.com", config.name)
    }

    @Test
    fun ipPayload_neverMatches_hostnameOnlyServer() {
        val s = server("s1", ip = "", hostname = "old.tunnel.com")
        val payload = QrPayload.Result(ip = "1.2.3.4", port = 8765, token = "t")
        val match = matchServer(listOf(s), payload)
        assertTrue("expected New, got $match", match is QrMatch.New)
    }
}
