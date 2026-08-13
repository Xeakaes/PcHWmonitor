package com.Obscrum.pchwmonitor

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.Obscrum.pchwmonitor.data.AppSettings

class BackgroundConnectionHandler(
    private val settingsProvider: () -> AppSettings,
    private val controller: MonitorController,
) : LifecycleEventObserver {

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> {
                val s = settingsProvider()
                controller.connect(s.serverIp, s.serverPort, s.authToken)
            }
            Lifecycle.Event.ON_STOP -> controller.disconnect()
            else -> Unit
        }
    }
}