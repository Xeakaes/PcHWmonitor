package com.Obscrum.pchwmonitor

import com.Obscrum.pchwmonitor.ui.dashboard.minAvgMax
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SummaryStatsTest {
    @Test
    fun emptyListReturnsNull() {
        assertNull(minAvgMax(emptyList()))
    }

    @Test
    fun computesMinAvgMax() {
        val result = minAvgMax(listOf(10f, 20f, 30f, 40f))
        assertEquals(10f, result?.first!!, 0.001f)
        assertEquals(25f, result?.second!!, 0.001f)
        assertEquals(40f, result?.third!!, 0.001f)
    }
}
