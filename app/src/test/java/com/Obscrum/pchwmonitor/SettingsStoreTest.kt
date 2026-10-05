package com.Obscrum.pchwmonitor

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.Obscrum.pchwmonitor.data.AppSettings
import com.Obscrum.pchwmonitor.data.ServerConfig
import com.Obscrum.pchwmonitor.data.SettingsStore
import com.Obscrum.pchwmonitor.data.ThemeMode
import com.Obscrum.pchwmonitor.ui.dashboard.DashboardLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class SettingsStoreTest {

    private class StoreHandle(
        val store: SettingsStore,
        val dataStore: DataStore<Preferences>,
        private val scope: CoroutineScope,
    ) {
        suspend fun close() {
            scope.cancel()
            scope.coroutineContext[Job]?.join()
        }
    }

    private fun store(tmpDir: File): StoreHandle {
        val scope = CoroutineScope(Dispatchers.IO)
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmpDir, "test.preferences_pb") },
        )
        return StoreHandle(SettingsStore(dataStore), dataStore, scope)
    }

    private fun tmpDir(): File = createTempDirectory().toFile()

    @Test
    fun defaultsAreApplied() = runTest {
        val handle = store(tmpDir())
        val settings = handle.store.settings.first()
        assertEquals(ThemeMode.SYSTEM, settings.theme)
        assertEquals(60, settings.chartWindowSeconds)
        assertEquals("default", settings.themePaletteId)
        assertFalse(settings.customBackgroundEnabled)
        assertFalse(settings.glassmorphismEnabled)
        assertNull(settings.customBackgroundUri)
        assertNull(settings.authToken)
        handle.close()
    }

    @Test
    fun writesRoundTrip() = runTest {
        val dir = tmpDir()
        val first = store(dir)
        first.store.setServerIp("10.0.0.5")
        first.store.setServerPort(9000)
        first.store.setTheme(ThemeMode.DARK)
        first.close()

        val second = store(dir)
        val settings = second.store.settings.first()
        assertEquals("10.0.0.5", settings.serverIp)
        assertEquals(9000, settings.serverPort)
        assertEquals(ThemeMode.DARK, settings.theme)
        second.close()
    }

    @Test
    fun chartWindowDefaultIs60Seconds() = runTest {
        val handle = store(tmpDir())
        val settings = handle.store.settings.first()
        assertEquals(60, settings.chartWindowSeconds)
        handle.close()
    }

    @Test
    fun chartWindowRoundTrip() = runTest {
        val dir = tmpDir()
        val first = store(dir)
        first.store.setChartWindowSeconds(300)
        first.close()

        val second = store(dir)
        assertEquals(300, second.store.settings.first().chartWindowSeconds)
        second.close()
    }

    @Test
    fun languageRoundTrip() = runTest {
        val dir = tmpDir()
        val first = store(dir)
        first.store.setLanguage("tr")
        first.close()

        val second = store(dir)
        assertEquals("tr", second.store.settings.first().language)
        second.store.setLanguage(null)
        assertEquals(null, second.store.settings.first().language)
        second.close()
    }

    @Test
    fun themePaletteDefaultsToDefault() = runTest {
        val handle = store(tmpDir())
        assertEquals("default", handle.store.settings.first().themePaletteId)
        handle.close()
    }

    @Test
    fun themePaletteRoundTrip() = runTest {
        val dir = tmpDir()
        val first = store(dir)
        first.store.setThemePalette("gold")
        first.close()

        val second = store(dir)
        assertEquals("gold", second.store.settings.first().themePaletteId)
        second.close()
    }

    @Test
    fun dashboardLayoutRoundTrip() = runTest {
        val dir = tmpDir()
        val layout = DashboardLayout.default()
        val first = store(dir)
        first.store.setDashboardLayout(layout)
        first.close()

        val second = store(dir)
        assertEquals(layout, second.store.settings.first().dashboardLayout)
        second.close()
    }

    @Test
    fun authTokenRoundTrip() = runTest {
        val dir = tmpDir()
        val first = store(dir)
        first.store.setAuthToken("sekret")
        first.close()

        val second = store(dir)
        assertEquals("sekret", second.store.settings.first().authToken)
        second.store.setAuthToken(null)
        assertEquals(null, second.store.settings.first().authToken)
        second.close()
    }

    @Test
    fun savePreferences_leavesConnectionFieldsUntouched() = runTest {
        val handle = store(tmpDir())
        handle.store.addServer(ServerConfig(id = "a", name = "A", ip = "10.0.0.1", port = 9000, token = "tok"))
        handle.store.savePreferences(ThemeMode.DARK, "de", 30, "midnight")
        val s = handle.store.settings.first()
        assertEquals("10.0.0.1", s.serverIp)
        assertEquals(9000, s.serverPort)
        assertEquals("tok", s.authToken)
        assertEquals(ThemeMode.DARK, s.theme)
        assertEquals("de", s.language)
        assertEquals(30, s.chartWindowSeconds)
        assertEquals("midnight", s.themePaletteId)
        assertEquals("10.0.0.1", s.servers.single().ip)
        handle.close()
    }

    @Test
    fun addServer_alwaysActivates_andMirrorsLegacy() = runTest {
        val handle = store(tmpDir())
        val a = ServerConfig(id = "a", name = "A", ip = "10.0.0.1", port = 9000, token = "tok-a")
        assertTrue(handle.store.addServer(a).isSuccess)
        var s = handle.store.settings.first()
        assertEquals("a", s.activeServerId)
        assertEquals("10.0.0.1", s.serverIp)
        assertEquals(9000, s.serverPort)
        assertEquals("tok-a", s.authToken)

        val b = ServerConfig(id = "b", name = "B", ip = "10.0.0.2", port = 9001, token = "tok-b")
        assertTrue(handle.store.addServer(b).isSuccess)
        s = handle.store.settings.first()
        assertEquals("b", s.activeServerId)
        assertEquals("10.0.0.2", s.serverIp)
        assertEquals(9001, s.serverPort)
        assertEquals("tok-b", s.authToken)
        handle.close()
    }

    @Test
    fun addServer_rejectsInvalidPort_andEmptyAddress() = runTest {
        val handle = store(tmpDir())
        assertTrue(handle.store.addServer(ServerConfig(id = "p", name = "P", ip = "10.0.0.1", port = 0)).isFailure)
        assertTrue(
            handle.store.addServer(ServerConfig(id = "e", name = "E", ip = "", port = 8765, hostname = null)).isFailure
        )
        assertTrue(handle.store.settings.first().servers.isEmpty())
        assertTrue(
            handle.store.addServer(
                ServerConfig(id = "h", name = "H", ip = "", port = 8765, hostname = "t.trycloudflare.com")
            ).isSuccess
        )
        handle.close()
    }

    @Test
    fun updateServerConnection_writesOnlyTargetRow_andMirrorsWhenActive() = runTest {
        val handle = store(tmpDir())
        val a = ServerConfig(id = "a", name = "A", ip = "10.0.0.1", port = 9000, token = "tok-a")
        val b = ServerConfig(id = "b", name = "B", ip = "10.0.0.2", port = 9001, token = "tok-b")
        handle.store.addServer(a)
        handle.store.addServer(b)

        val result = handle.store.updateServerConnection("a", "A2", "10.0.0.9", 7777, "newtok", null)
        assertTrue(result.isSuccess)

        var s = handle.store.settings.first()
        val rowA = s.servers.first { it.id == "a" }
        assertEquals("A2", rowA.name)
        assertEquals("10.0.0.9", rowA.ip)
        assertEquals(7777, rowA.port)
        assertEquals("newtok", rowA.token)
        assertEquals(b, s.servers.first { it.id == "b" })
        assertEquals("b", s.activeServerId)
        assertEquals("10.0.0.2", s.serverIp)

        handle.store.setActiveServerId("a")
        s = handle.store.settings.first()
        assertEquals("10.0.0.9", s.serverIp)
        assertEquals(7777, s.serverPort)
        handle.close()
    }

    @Test
    fun updateServerConnection_hostnameSetsUseTls_andClearLeavesIt() = runTest {
        val handle = store(tmpDir())
        handle.store.addServer(ServerConfig(id = "a", name = "A", ip = "10.0.0.1", port = 9000, useTls = false))
        assertTrue(
            handle.store.updateServerConnection("a", "A", "10.0.0.1", 9000, null, "x.trycloudflare.com").isSuccess
        )
        var s = handle.store.settings.first()
        assertEquals("x.trycloudflare.com", s.servers.single().hostname)
        assertTrue(s.servers.single().useTls)

        assertTrue(handle.store.updateServerConnection("a", "A", "10.0.0.1", 9000, null, "").isSuccess)
        s = handle.store.settings.first()
        assertNull(s.servers.single().hostname)
        assertTrue(s.servers.single().useTls)
        handle.close()
    }

    @Test
    fun activeServerId_danglingFallsBackToFirst() = runTest {
        val handle = store(tmpDir())
        handle.store.addServer(ServerConfig(id = "a", name = "A", ip = "10.0.0.1", port = 9000))
        handle.store.setActiveServerId("ghost")
        assertEquals("a", handle.store.settings.first().activeServerId)
        handle.close()
    }

    @Test
    fun corrupt_serversJson_readPreservesRaw_andReturnsEmpty() = runTest {
        val handle = store(tmpDir())
        val key = stringPreferencesKey("servers_json")
        handle.dataStore.edit { it[key] = "{not-json" }
        val s = handle.store.settings.first()
        assertTrue(s.servers.isEmpty())
        assertEquals("{not-json", handle.dataStore.data.first()[key])
        handle.close()
    }

    @Test
    fun removeServer_lastServer_clearsActive() = runTest {
        val handle = store(tmpDir())
        handle.store.addServer(ServerConfig(id = "a", name = "A", ip = "10.0.0.1", port = 9000))
        handle.store.removeServer("a")
        val s = handle.store.settings.first()
        assertTrue(s.servers.isEmpty())
        assertNull(s.activeServerId)
        assertEquals("10.0.0.1", s.serverIp)
        handle.close()
    }

    @Test
    fun backgroundConnectAll_persists() = runTest {
        val handle = store(tmpDir())
        handle.store.setBackgroundConnectAll(true)
        assertTrue(handle.store.settings.first().backgroundConnectAll)
        handle.close()
    }

    @Test
    fun legacyKeysPresent_migratesAndWritesBackExactlyOnce() = runTest {
        val dir = tmpDir()
        val first = store(dir)
        first.dataStore.edit {
            it[stringPreferencesKey("server_ip")] = "192.168.1.50"
            it[intPreferencesKey("server_port")] = 9010
            it[stringPreferencesKey("auth_token")] = "legacytok"
        }
        val s1 = first.store.settings.first()
        assertEquals(1, s1.servers.size)
        assertEquals("192.168.1.50", s1.servers.single().ip)
        assertEquals(9010, s1.servers.single().port)
        assertEquals("legacytok", s1.servers.single().token)
        val s2 = first.store.settings.first()          // second emission: write-back must be stable
        assertEquals(s1.servers, s2.servers)
        first.close()
        val second = store(dir)                         // fresh DataStore read = persisted write-back
        assertEquals("192.168.1.50", second.store.settings.first().servers.single().ip)
        second.close()
    }

    @Test
    fun noLegacyKeys_doesNotWriteBack() = runTest {
        val handle = store(tmpDir())
        handle.store.settings.first()
        val raw = handle.dataStore.data.first()[stringPreferencesKey("servers_json")]
        assertNull(raw)                                  // untouched — no migration write
        assertTrue(handle.store.settings.first().servers.isEmpty())
        handle.close()
    }
}
