package com.Obscrum.pchwmonitor.data.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket

class DiscoveryService(
    private val scope: CoroutineScope,
    private val context: Context? = null,
) {
    companion object {
        private const val TAG = "DiscoveryService"
        private const val BROADCAST_PORT = 8766
        private const val MAGIC = "PCHW"
        private const val BUFFER_SIZE = 1024
    }

    data class DiscoveredServer(
        val name: String,
        val ip: String,
        val port: Int,
        val version: String,
    )

    private val _servers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val servers: StateFlow<List<DiscoveredServer>> = _servers.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null

    fun startScan(timeoutMs: Long = 5000L) {
        if (_isScanning.value) return

        scanJob = scope.launch(Dispatchers.IO) {
            _isScanning.value = true
            _servers.value = emptyList()

            val discovered = mutableListOf<DiscoveredServer>()
            var socket: DatagramSocket? = null
            var multicastLock: WifiManager.MulticastLock? = null

            try {
                context?.let { ctx ->
                    val wifiManager = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    wifiManager?.let {
                        multicastLock = it.createMulticastLock("pchw_discovery").apply {
                            setReferenceCounted(true)
                            acquire()
                        }
                    }
                }

                socket = DatagramSocket(BROADCAST_PORT)
                socket.broadcast = true
                socket.soTimeout = timeoutMs.toInt()

                val buffer = ByteArray(BUFFER_SIZE)
                val packet = DatagramPacket(buffer, buffer.size)
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    try {
                        socket.receive(packet)
                        val message = String(packet.data, 0, packet.length)
                        val json = JSONObject(message)

                        if (json.optString("magic") == MAGIC) {
                            val server = DiscoveredServer(
                                name = json.optString("name", "Unknown"),
                                ip = json.optString("ip", ""),
                                port = json.optInt("port", 8765),
                                version = json.optString("version", "unknown"),
                            )

                            if (discovered.none { it.ip == server.ip && it.port == server.port }) {
                                discovered.add(server)
                                _servers.value = discovered.toList()
                                Log.d(TAG, "Discovered server: ${server.name} at ${server.ip}:${server.port}")
                            }
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discovery scan failed", e)
            } finally {
                socket?.close()
                multicastLock?.release()
                _isScanning.value = false
                Log.d(TAG, "Discovery scan complete, found ${discovered.size} servers")
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _isScanning.value = false
    }
}
