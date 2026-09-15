package com.Obscrum.pchwmonitor

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.Obscrum.pchwmonitor.data.ServerConfig
import com.Obscrum.pchwmonitor.data.network.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeOwner : LifecycleOwner {
    override val lifecycle: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
}

class BackgroundConnectionHandlerTest {

    @Test
    fun stopsOnBackgroundAndReconnectsWithLatestSettingsOnForeground() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val client = FakeWsClient()
        val controller = MonitorController(client, FakeHistoryStore(), scope, pcId = "default")
        controller.start()

        val owner = FakeOwner()
        var servers = listOf(ServerConfig(id = "default", name = "PC1", ip = "192.168.1.50", port = 8765, token = "tok"))
        val controllers = mutableMapOf("default" to controller)
        val handler = BackgroundConnectionHandler({ servers }, controllers)

        handler.onStateChanged(owner, Lifecycle.Event.ON_START)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, client.urls.size)
        assertEquals("ws://192.168.1.50:8765/ws", client.urls.single())
        assertEquals("tok", client.tokens.single())

        handler.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ConnectionState.DISCONNECTED, controller.connection.value)

        servers = listOf(ServerConfig(id = "default", name = "PC1", ip = "10.0.0.9", port = 8765))
        handler.onStateChanged(owner, Lifecycle.Event.ON_START)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("ws://192.168.1.50:8765/ws", "ws://10.0.0.9:8765/ws"), client.urls)
        assertEquals(ConnectionState.CONNECTED, controller.connection.value)
    }

    @Test
    fun ignoresIntermediateLifecycleEvents() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val client = FakeWsClient()
        val controller = MonitorController(client, FakeHistoryStore(), scope, pcId = "default")
        controller.start()

        val owner = FakeOwner()
        val servers = listOf(ServerConfig(id = "default", name = "PC1", ip = "192.168.1.50"))
        val controllers = mapOf("default" to controller)
        val handler = BackgroundConnectionHandler({ servers }, controllers)

        handler.onStateChanged(owner, Lifecycle.Event.ON_CREATE)
        handler.onStateChanged(owner, Lifecycle.Event.ON_PAUSE)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, client.urls.size)
    }
}
