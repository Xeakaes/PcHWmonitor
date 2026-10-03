package com.Obscrum.pchwmonitor

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.Obscrum.pchwmonitor.data.IssueCode
import com.Obscrum.pchwmonitor.data.IssueSeverity
import com.Obscrum.pchwmonitor.data.ServerConfig
import com.Obscrum.pchwmonitor.data.SettingsStore
import com.Obscrum.pchwmonitor.ui.servers.ServerEditViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCoroutinesApi::class)
class ServerEditViewModelTest {

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

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun editMode_prefillsFromStore_andSaveUpdatesThatRowOnly() = runTest {
        val handle = store(tmpDir())
        handle.store.addServer(ServerConfig(id = "a", name = "A", ip = "1.1.1.1", port = 9000))
        handle.store.addServer(ServerConfig(id = "b", name = "B", ip = "2.2.2.2", port = 9001))

        val vm = ServerEditViewModel(handle.store, serverId = "a")
        assertEquals("1.1.1.1", vm.ip.first { it == "1.1.1.1" })
        assertEquals("A", vm.name.value)
        assertEquals("9000", vm.port.value)
        assertFalse(vm.isNew)

        vm.ip.value = "3.3.3.3"
        assertTrue(vm.save())

        val s = handle.store.settings.first()
        assertEquals("3.3.3.3", s.servers.first { it.id == "a" }.ip)
        assertEquals("2.2.2.2", s.servers.first { it.id == "b" }.ip)
        handle.close()
    }

    @Test
    fun save_blockingIssue_returnsFalse_andWritesNothing() = runTest {
        val handle = store(tmpDir())
        val vm = ServerEditViewModel(handle.store, serverId = null)
        vm.port.first { it == "8765" }
        vm.ip.value = ""
        vm.port.value = "abc"

        assertFalse(vm.save())

        val issues = vm.issues.value
        assertTrue(issues.any { it.code == IssueCode.EMPTY_IP && it.severity == IssueSeverity.ERROR })
        assertTrue(issues.any { it.code == IssueCode.INVALID_PORT && it.severity == IssueSeverity.ERROR })
        assertTrue(handle.store.settings.first().servers.isEmpty())
        handle.close()
    }

    @Test
    fun newMode_prefillsPort8765_andSavesWithGeneratedId() = runTest {
        val handle = store(tmpDir())
        val vm = ServerEditViewModel(handle.store, serverId = null)
        assertTrue(vm.isNew)
        vm.port.first { it == "8765" }

        vm.name.value = "New PC"
        vm.ip.value = "10.0.0.7"
        assertTrue(vm.save())

        val s = handle.store.settings.first()
        assertEquals(1, s.servers.size)
        val saved = s.servers.single()
        assertTrue(saved.id.isNotBlank())
        assertEquals(8765, saved.port)
        assertEquals("New PC", saved.name)
        assertEquals("10.0.0.7", saved.ip)
        handle.close()
    }

    @Test
    fun duplicateName_isWarning_only() = runTest {
        val handle = store(tmpDir())
        handle.store.addServer(ServerConfig(id = "pc1", name = "PC", ip = "192.168.1.5", port = 8765))

        val vm = ServerEditViewModel(handle.store, serverId = null)
        vm.port.first { it == "8765" }
        vm.name.value = "pc"
        vm.ip.value = "10.0.0.8"

        assertTrue(vm.save())
        assertTrue(
            vm.issues.value.any {
                it.code == IssueCode.DUPLICATE_NAME && it.severity == IssueSeverity.WARNING
            },
        )
        handle.close()
    }
}
