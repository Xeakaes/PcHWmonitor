package com.Obscrum.pchwmonitor

import com.Obscrum.pchwmonitor.data.local.HistorySample
import com.Obscrum.pchwmonitor.data.local.HistoryStore
import com.Obscrum.pchwmonitor.data.network.ConnectionState
import com.Obscrum.pchwmonitor.data.network.StatusParser
import com.Obscrum.pchwmonitor.data.network.WsClient
import com.Obscrum.pchwmonitor.domain.model.CpuInfo
import com.Obscrum.pchwmonitor.domain.model.SystemStatus
import com.Obscrum.pchwmonitor.domain.model.WsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeWsClient : WsClient {
    private val _messages = MutableSharedFlow<WsMessage>(extraBufferCapacity = 32)
    override val messages: SharedFlow<WsMessage> = _messages
    override val connectionState: MutableStateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.DISCONNECTED)
    val urls = mutableListOf<String>()

    override fun connect(url: String) {
        urls.add(url)
        connectionState.value = ConnectionState.CONNECTED
    }

    override fun disconnect() {
        connectionState.value = ConnectionState.DISCONNECTED
    }

    suspend fun emit(message: WsMessage) {
        _messages.emit(message)
    }
}

class FakeHistoryStore : HistoryStore {
    val recorded = mutableListOf<SystemStatus>()

    override suspend fun record(status: SystemStatus) {
        recorded.add(status)
    }

    override suspend fun history(start: Long): List<HistorySample> = emptyList()
}

class MonitorControllerTest {

    private fun status(ts: Long, available: Boolean = true) = SystemStatus(
        timestamp = ts,
        available = available,
        error = if (available) null else "boom",
        cpu = CpuInfo(tempC = 60f),
    )

    @Test
    fun connectBuildsUrlAndExposesStatus() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val client = FakeWsClient()
        val controller = MonitorController(client, FakeHistoryStore(), scope)

        controller.start()
        controller.connect("192.168.1.5", 8765)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("ws://192.168.1.5:8765/ws"), client.urls)
        assertEquals(ConnectionState.CONNECTED, controller.connection.value)

        client.emit(WsMessage.Status(status(1000L)))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(60f, controller.status.value?.cpu?.tempC!!, 0.001f)
        assertNull(controller.lastError.value)
    }

    @Test
    fun sameUrlDoesNotReconnect() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val client = FakeWsClient()
        val controller = MonitorController(client, FakeHistoryStore(), scope)

        controller.start()
        controller.connect("10.0.0.1", 8765)
        controller.connect("10.0.0.1", 8765)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, client.urls.size)
    }

    @Test
    fun recordsHistoryWithIntervalThrottle() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val client = FakeWsClient()
        val history = FakeHistoryStore()
        val controller = MonitorController(client, history, scope, recordIntervalMs = 5_000L)

        controller.start()
        controller.connect("10.0.0.1", 8765)
        dispatcher.scheduler.advanceUntilIdle()

        client.emit(WsMessage.Status(status(1_000L)))
        client.emit(WsMessage.Status(status(3_000L)))
        client.emit(WsMessage.Status(status(6_000L)))
        client.emit(WsMessage.Status(status(11_000L)))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(1_000L, 6_000L, 11_000L), history.recorded.map { it.timestamp })
    }

    @Test
    fun recordsHistoryFromRealServerSecondPayloads() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val client = FakeWsClient()
        val history = FakeHistoryStore()
        val controller = MonitorController(client, history, scope, recordIntervalMs = 5_000L)

        controller.start()
        controller.connect("10.0.0.1", 8765)
        dispatcher.scheduler.advanceUntilIdle()

        for (ts in listOf(1_754_150_000L, 1_754_150_001L, 1_754_150_004L, 1_754_150_009L)) {
            val raw = """{"type":"status","timestamp":$ts,"available":true,"cpu":{"name":"C","tempC":60.0}}"""
            client.emit(StatusParser.parse(raw))
        }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(1_754_150_000_000L, 1_754_150_009_000L),
            history.recorded.map { it.timestamp },
        )
    }

    @Test
    fun unavailableStatusSetsErrorAndSkipsRecording() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val client = FakeWsClient()
        val history = FakeHistoryStore()
        val controller = MonitorController(client, history, scope, recordIntervalMs = 5_000L)

        controller.start()
        controller.connect("10.0.0.1", 8765)
        dispatcher.scheduler.advanceUntilIdle()

        client.emit(WsMessage.Status(status(1_000L, available = false)))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("boom", controller.lastError.value)
        assertTrue(history.recorded.isEmpty())
    }
}
