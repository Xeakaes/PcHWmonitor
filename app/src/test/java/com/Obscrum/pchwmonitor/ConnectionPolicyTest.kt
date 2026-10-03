package com.Obscrum.pchwmonitor

import com.Obscrum.pchwmonitor.data.AppSettings
import com.Obscrum.pchwmonitor.data.ConnectionPlan
import com.Obscrum.pchwmonitor.data.ServerConfig
import com.Obscrum.pchwmonitor.data.ThemeMode
import com.Obscrum.pchwmonitor.data.planConnections
import com.Obscrum.pchwmonitor.data.planOnStop
import com.Obscrum.pchwmonitor.data.toConnectionKey
import com.Obscrum.pchwmonitor.ui.dashboard.DashboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionPolicyTest {

    private fun cfg(
        id: String,
        ip: String = "1.2.3.4",
        port: Int = 8765,
        token: String? = "tok",
        hostname: String? = null,
        useTls: Boolean = false,
        trustedCertHash: String? = null,
        name: String = "name-$id",
    ) = ServerConfig(
        id = id,
        name = name,
        ip = ip,
        port = port,
        token = token,
        trustedCertHash = trustedCertHash,
        useTls = useTls,
        hostname = hostname,
    )

    private fun applyPlan(current: Map<String, ServerConfig>, plan: ConnectionPlan): Map<String, ServerConfig> {
        val updated = current - plan.disconnect - plan.dispose + plan.connect
        return updated
    }

    @Test
    fun plan_activeOnly_connectsActive_disconnectsRest() {
        val a = cfg("a")
        val b = cfg("b")
        val c = cfg("c")
        val plan = planConnections(
            servers = listOf(a, b, c),
            activeId = "c",
            backgroundAll = false,
            current = mapOf("a" to a, "b" to b),
        )
        assertEquals(mapOf("c" to c), plan.connect)
        assertEquals(setOf("a", "b"), plan.disconnect)
        assertEquals(emptySet<String>(), plan.dispose)
    }

    @Test
    fun plan_desiredAlreadyConnected_isKept() {
        val a = cfg("a")
        val b = cfg("b")
        val c = cfg("c")
        val plan = planConnections(
            servers = listOf(a, b, c),
            activeId = "c",
            backgroundAll = false,
            current = mapOf("a" to a, "b" to b, "c" to c),
        )
        assertEquals(emptyMap<String, ServerConfig>(), plan.connect)
        assertEquals(setOf("a", "b"), plan.disconnect)
    }

    @Test
    fun plan_backgroundAll_connectsAllMissing() {
        val a = cfg("a")
        val b = cfg("b")
        val plan = planConnections(
            servers = listOf(a, b),
            activeId = "a",
            backgroundAll = true,
            current = emptyMap(),
        )
        assertEquals(setOf("a", "b"), plan.connect.keys)
        assertEquals(emptySet<String>(), plan.disconnect)
    }

    @Test
    fun plan_nullActive_connectsNothing_disconnectsAll() {
        val a = cfg("a")
        val plan = planConnections(
            servers = listOf(a),
            activeId = null,
            backgroundAll = false,
            current = mapOf("a" to a),
        )
        assertEquals(setOf("a"), plan.disconnect)
        assertEquals(emptyMap<String, ServerConfig>(), plan.connect)
    }

    @Test
    fun plan_deletedServer_isDisposed() {
        val a = cfg("a")
        val b = cfg("b")
        val plan = planConnections(
            servers = listOf(b),
            activeId = "b",
            backgroundAll = false,
            current = mapOf("a" to a, "b" to b),
        )
        assertEquals(setOf("a"), plan.dispose)
        assertFalse("disposed id must not also be disconnected", plan.disconnect.contains("a"))
        assertEquals(emptyMap<String, ServerConfig>(), plan.connect)
    }

    @Test
    fun plan_paramChange_reconnectsOnlyChanged() {
        val aSigX = cfg("a", ip = "10.0.0.1")
        val b = cfg("b")
        val aSigY = cfg("a", ip = "10.0.0.2")

        val plan1 = planConnections(
            servers = listOf(aSigY, b),
            activeId = "a",
            backgroundAll = false,
            current = mapOf("a" to aSigX, "b" to b),
        )
        assertEquals(setOf("a", "b"), plan1.disconnect)
        assertEquals(mapOf("a" to aSigY), plan1.connect)

        val converged = mapOf("a" to aSigY, "b" to b)
        val plan2 = planConnections(
            servers = listOf(aSigY, b),
            activeId = "a",
            backgroundAll = true,
            current = converged,
        )
        assertEquals(emptyMap<String, ServerConfig>(), plan2.connect)
        assertEquals(emptySet<String>(), plan2.disconnect)
        assertEquals(emptySet<String>(), plan2.dispose)
    }

    @Test
    fun plan_nameChange_doesNotReconnect() {
        val aOld = cfg("a", name = "Old Name")
        val b = cfg("b")
        val aNew = cfg("a", name = "New Name")

        val plan = planConnections(
            servers = listOf(aNew, b),
            activeId = "a",
            backgroundAll = true,
            current = mapOf("a" to aOld, "b" to b),
        )
        assertEquals(emptyMap<String, ServerConfig>(), plan.connect)
        assertEquals(emptySet<String>(), plan.disconnect)
        assertEquals(emptySet<String>(), plan.dispose)
    }

    @Test
    fun plan_isIdempotent_overRepeatedActiveChange() {
        val a = cfg("a")
        val b = cfg("b")
        val c = cfg("c")
        val servers = listOf(a, b, c)

        val p1 = planConnections(
            servers = servers,
            activeId = "c",
            backgroundAll = false,
            current = mapOf("a" to a),
        )
        val afterP1 = applyPlan(mapOf("a" to a), p1)
        assertEquals(setOf("c"), afterP1.keys)

        val p2 = planConnections(
            servers = servers,
            activeId = "c",
            backgroundAll = false,
            current = afterP1,
        )
        assertTrue(p2.connect.isEmpty())
        assertTrue(p2.disconnect.isEmpty())
        assertTrue(p2.dispose.isEmpty())
    }

    @Test
    fun connectionKey_ignoresAllPreferenceFields() {
        val s1 = AppSettings(backgroundConnectAll = false)
        val s2 = s1.copy(
            theme = ThemeMode.DARK,
            language = "de",
            chartWindowSeconds = 30,
            themePaletteId = "neon",
            customBackgroundEnabled = true,
            glassmorphismEnabled = true,
            dashboardLayout = DashboardLayout(listOf()),
            customBackgroundUri = "content://bg",
        )
        assertEquals(s1.toConnectionKey(), s2.toConnectionKey())

        assertNotEquals(
            s1.toConnectionKey(),
            s1.copy(backgroundConnectAll = !s1.backgroundConnectAll).toConnectionKey(),
        )
        assertNotEquals(
            s1.toConnectionKey(),
            s1.copy(activeServerId = "x").toConnectionKey(),
        )
    }

    @Test
    fun planOnStop_offDisconnectsAll_onKeepsAll() {
        val a = cfg("a")
        val b = cfg("b")
        val current = mapOf("a" to a, "b" to b)

        val off = planOnStop(current = current, backgroundAll = false)
        assertEquals(emptyMap<String, ServerConfig>(), off.connect)
        assertEquals(setOf("a", "b"), off.disconnect)
        assertEquals(emptySet<String>(), off.dispose)

        val on = planOnStop(current = current, backgroundAll = true)
        assertEquals(emptyMap<String, ServerConfig>(), on.connect)
        assertEquals(emptySet<String>(), on.disconnect)
        assertEquals(emptySet<String>(), on.dispose)
    }
}
