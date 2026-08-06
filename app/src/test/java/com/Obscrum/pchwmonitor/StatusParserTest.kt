package com.Obscrum.pchwmonitor

import com.Obscrum.pchwmonitor.data.network.StatusParser
import com.Obscrum.pchwmonitor.domain.model.WsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusParserTest {

    private val fullStatus = """
        {
          "type": "status",
          "timestamp": 1754150000,
          "available": true,
          "pc": {"name": "DESKTOP-ABC", "os": "Windows 11", "source": "librehardwaremonitor"},
          "cpu": {"name": "Intel Core i7-13700K", "usagePct": 34.5, "tempC": 61.2, "clockMhz": 5100.0,
                  "powerW": 125.0, "loads": [12.3, 45.2, 33.1]},
          "gpu": {"name": "RTX 4070", "usagePct": 78.3, "tempC": 71.4, "hotspotC": 84.1,
                  "vramUsedMb": 6112.0, "vramTotalMb": 12288.0, "coreClockMhz": 2745.0,
                  "memClockMhz": 10500.0, "powerW": 182.0, "fps": null},
          "ram": {"usedGb": 11.2, "totalGb": 32.0, "usagePct": 35.0, "clockMhz": 3600.0}
        }
    """.trimIndent()

    @Test
    fun parsesFullStatusMessage() {
        val msg = StatusParser.parse(fullStatus) as WsMessage.Status
        assertEquals(1754150000_000L, msg.status.timestamp)
        assertTrue(msg.status.available)
        assertEquals("DESKTOP-ABC", msg.status.pc?.name)
        assertEquals(61.2f, msg.status.cpu?.tempC!!, 0.001f)
        assertEquals(listOf(12.3f, 45.2f, 33.1f), msg.status.cpu?.loads)
        assertEquals(84.1f, msg.status.gpu?.hotspotC!!, 0.001f)
        assertEquals(12288.0f, msg.status.gpu?.vramTotalMb!!, 0.001f)
        assertEquals(11.2f, msg.status.ram?.usedGb!!, 0.001f)
    }

    @Test
    fun parsesMessageWithMissingSectionsAsNull() {
        val raw = """{"type":"status","timestamp":1,"available":false,"error":"boom"}"""
        val msg = StatusParser.parse(raw) as WsMessage.Status
        assertNull(msg.status.cpu)
        assertNull(msg.status.gpu)
        assertNull(msg.status.ram)
        assertNull(msg.status.pc)
        assertEquals("boom", msg.status.error)
    }

    @Test
    fun ignoresUnknownKeys() {
        val raw = """{"type":"status","timestamp":2,"extra":"x","nested":{"a":1},"cpu":{"name":"C","bogus":true}}"""
        val msg = StatusParser.parse(raw) as WsMessage.Status
        assertEquals(2_000L, msg.status.timestamp)
        assertEquals("C", msg.status.cpu?.name)
    }

    @Test
    fun convertsServerSecondsTimestampToMilliseconds() {
        val raw = """{"type":"status","timestamp":1754150000}"""
        val msg = StatusParser.parse(raw) as WsMessage.Status
        assertEquals(1754150000_000L, msg.status.timestamp)
    }

    @Test
    fun parsesWelcome() {
        val raw = """{"type":"welcome","intervalMs":1000,"serverName":"DESKTOP-ABC","source":"simulator","pcName":"DESKTOP-ABC"}"""
        val msg = StatusParser.parse(raw) as WsMessage.Welcome
        assertEquals(1000, msg.info.intervalMs)
        assertEquals("simulator", msg.info.source)
    }

    @Test
    fun malformedJsonYieldsParseFailure() {
        val msg = StatusParser.parse("not json at all")
        assertTrue(msg is WsMessage.ParseFailure)
    }

    @Test
    fun unknownTypeYieldsParseFailure() {
        val msg = StatusParser.parse("""{"type":"mystery"}""")
        assertTrue(msg is WsMessage.ParseFailure)
    }

    @Test
    fun parseStatusWithIgpu() {
        val raw = """{"type":"status","timestamp":1,"igpu":{"name":"Intel UHD Graphics","usagePct":12.5}}"""
        val status = (StatusParser.parse(raw) as WsMessage.Status).status
        assertEquals("Intel UHD Graphics", status.igpu?.name)
        assertEquals(12.5f, status.igpu?.usagePct!!, 0.001f)
    }

    @Test
    fun parseStatusWithoutIgpuKeepsNull() {
        val raw = """{"type":"status","timestamp":1,"gpu":{"name":"RTX"}}"""
        val status = (StatusParser.parse(raw) as WsMessage.Status).status
        assertNull(status.igpu)
    }

    @Test
    fun parsesV13MetricsAndFps() {
        val raw = """
            {"type":"status","timestamp":1754150000,
             "disk":{"usagePct":42.5,"readMbPerSec":180.2,"writeMbPerSec":64.1},
             "net":{"downloadMbPerSec":12.4,"uploadMbPerSec":3.2},
             "fans":[{"label":"CPU Fan","rpm":1150.0},{"label":"Case Fan","rpm":800.0}],
             "fps":{"name":"game.exe","current":120.0,"avg":117.3,"onePercentLow":92.0}}
        """.trimIndent()
        val status = (StatusParser.parse(raw) as WsMessage.Status).status
        assertEquals(42.5f, status.disk?.usagePct!!, 0.001f)
        assertEquals(64.1f, status.disk?.writeMbPerSec!!, 0.001f)
        assertEquals(12.4f, status.net?.downloadMbPerSec!!, 0.001f)
        assertEquals(listOf("CPU Fan", "Case Fan"), status.fans?.map { it.label })
        assertEquals(1150.0f, status.fans?.get(0)?.rpm!!, 0.001f)
        assertEquals("game.exe", status.fps?.name)
        assertEquals(92.0f, status.fps?.onePercentLow!!, 0.001f)
    }

    @Test
    fun v13SectionsAbsentAreNull() {
        val raw = """{"type":"status","timestamp":1}"""
        val status = (StatusParser.parse(raw) as WsMessage.Status).status
        assertNull(status.disk)
        assertNull(status.net)
        assertNull(status.fans)
        assertNull(status.fps)
    }
}
