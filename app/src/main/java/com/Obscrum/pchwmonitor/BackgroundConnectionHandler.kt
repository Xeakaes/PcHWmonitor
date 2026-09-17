package com.Obscrum.pchwmonitor

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.Obscrum.pchwmonitor.data.ServerConfig

class BackgroundConnectionHandler(
    private val serversProvider: () -> List<ServerConfig>,
    private val controllers: Map<String, MonitorController>,
) : LifecycleEventObserver {

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> {
                serversProvider().forEach { cfg ->
                    controllers[cfg.id]?.connect(cfg.ip, cfg.port, cfg.token, cfg.useTls, cfg.hostname)
                }
            }
            Lifecycle.Event.ON_STOP -> {
                controllers.values.forEach { it.disconnect() }
            }
            else -> Unit
        }
    }
}