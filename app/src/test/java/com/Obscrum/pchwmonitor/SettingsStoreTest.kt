package com.Obscrum.pchwmonitor

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.Obscrum.pchwmonitor.data.AppSettings
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
import org.junit.Test
import java.io.File

class SettingsStoreTest {

    private class StoreHandle(val store: SettingsStore, private val scope: CoroutineScope) {
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
        return StoreHandle(SettingsStore(dataStore), scope)
    }

    @Test
    fun defaultsAreApplied() = runTest {
        val handle = store(createTempDir())
        val settings = handle.store.settings.first()
        assertEquals(AppSettings(), settings)
        assertEquals(ThemeMode.SYSTEM, settings.theme)
        handle.close()
    }

    @Test
    fun writesRoundTrip() = runTest {
        val dir = createTempDir()
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
        val handle = store(createTempDir())
        val settings = handle.store.settings.first()
        assertEquals(60, settings.chartWindowSeconds)
        handle.close()
    }

    @Test
    fun chartWindowRoundTrip() = runTest {
        val dir = createTempDir()
        val first = store(dir)
        first.store.setChartWindowSeconds(300)
        first.close()

        val second = store(dir)
        assertEquals(300, second.store.settings.first().chartWindowSeconds)
        second.close()
    }

    @Test
    fun languageRoundTrip() = runTest {
        val dir = createTempDir()
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
        val handle = store(createTempDir())
        assertEquals("default", handle.store.settings.first().themePaletteId)
        handle.close()
    }

    @Test
    fun themePaletteRoundTrip() = runTest {
        val dir = createTempDir()
        val first = store(dir)
        first.store.setThemePalette("gold")
        first.close()

        val second = store(dir)
        assertEquals("gold", second.store.settings.first().themePaletteId)
        second.close()
    }

    @Test
    fun dashboardLayoutRoundTrip() = runTest {
        val dir = createTempDir()
        val layout = DashboardLayout.default()
        val first = store(dir)
        first.store.setDashboardLayout(layout)
        first.close()

        val second = store(dir)
        assertEquals(layout, second.store.settings.first().dashboardLayout)
        second.close()
    }
}
