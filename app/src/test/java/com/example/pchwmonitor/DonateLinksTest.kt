package com.example.pchwmonitor

import com.example.pchwmonitor.util.PATREON_URL
import org.junit.Assert.assertEquals
import org.junit.Test

class DonateLinksTest {
    @Test
    fun patreonUrlIsCorrect() {
        assertEquals("https://www.patreon.com/cw/Obscrum", PATREON_URL)
    }
}
