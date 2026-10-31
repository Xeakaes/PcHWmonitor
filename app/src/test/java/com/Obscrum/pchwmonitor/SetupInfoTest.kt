package com.Obscrum.pchwmonitor

import com.Obscrum.pchwmonitor.data.SetupInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupInfoTest {

    @Test
    fun showsWhenNeverShownAndNoServers() {
        assertTrue(SetupInfo.shouldShow(setupInfoShown = false, serverCount = 0))
    }

    @Test
    fun hidesWhenFlagSet() {
        assertFalse(SetupInfo.shouldShow(setupInfoShown = true, serverCount = 0))
    }

    @Test
    fun hidesWhenServersExist() {
        assertFalse(SetupInfo.shouldShow(setupInfoShown = false, serverCount = 2))
    }
}
