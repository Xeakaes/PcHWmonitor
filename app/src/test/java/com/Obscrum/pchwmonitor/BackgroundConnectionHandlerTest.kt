package com.Obscrum.pchwmonitor

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeOwner : LifecycleOwner {
    override val lifecycle: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
}

class BackgroundConnectionHandlerTest {

    @Test
    fun onStart_invokesOnForegroundOnce() {
        val owner = FakeOwner()
        var foreground = 0
        var background = 0
        val handler = BackgroundConnectionHandler(
            onForeground = { foreground++ },
            onBackground = { background++ },
        )

        handler.onStateChanged(owner, Lifecycle.Event.ON_START)

        assertEquals(1, foreground)
        assertEquals(0, background)
    }

    @Test
    fun onStop_invokesOnBackgroundOnce() {
        val owner = FakeOwner()
        var foreground = 0
        var background = 0
        val handler = BackgroundConnectionHandler(
            onForeground = { foreground++ },
            onBackground = { background++ },
        )

        handler.onStateChanged(owner, Lifecycle.Event.ON_STOP)

        assertEquals(1, background)
        assertEquals(0, foreground)
    }

    @Test
    fun onResumeAndOnPause_invokeNeither() {
        val owner = FakeOwner()
        var foreground = 0
        var background = 0
        val handler = BackgroundConnectionHandler(
            onForeground = { foreground++ },
            onBackground = { background++ },
        )

        handler.onStateChanged(owner, Lifecycle.Event.ON_RESUME)
        handler.onStateChanged(owner, Lifecycle.Event.ON_PAUSE)

        assertEquals(0, foreground)
        assertEquals(0, background)
    }
}
