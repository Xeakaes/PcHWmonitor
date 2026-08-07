package com.Obscrum.pchwmonitor

import com.Obscrum.pchwmonitor.ui.dashboard.CardId
import com.Obscrum.pchwmonitor.ui.dashboard.DashboardLayout
import com.Obscrum.pchwmonitor.ui.dashboard.LayoutEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardLayoutTest {

    @Test
    fun defaultHasEightEntriesAllVisible() {
        val layout = DashboardLayout.default()
        assertEquals(8, layout.entries.size)
        assertEquals(
            listOf(CardId.CPU, CardId.GPU, CardId.IGPU, CardId.FPS, CardId.RAM, CardId.DISK, CardId.NET, CardId.FAN),
            layout.entries.map { it.card },
        )
        assertEquals(8, layout.visibleEntries().size)
    }

    @Test
    fun defaultPinnedOrderIsCpuGpuFpsRam() {
        val layout = DashboardLayout.default()
        assertEquals(
            listOf(CardId.CPU, CardId.GPU, CardId.FPS, CardId.RAM),
            layout.entries.filter { it.pinned }.map { it.card },
        )
        assertFalse(layout.entries.first { it.card == CardId.IGPU }.pinned)
    }

    @Test
    fun fromJsonRoundTripsOwnOutput() {
        val layout = DashboardLayout.default()
        assertEquals(layout, DashboardLayout().fromJson(layout.toJson()))
    }

    @Test
    fun unknownCardIsSkippedDuringParse() {
        val json = """[{"card":"bogus","visible":true,"pinned":false},{"card":"cpu","visible":false,"pinned":true}]"""
        val layout = DashboardLayout().fromJson(json)
        assertEquals(listOf(CardId.CPU), layout.entries.map { it.card })
        assertFalse(layout.entries[0].visible)
        assertTrue(layout.entries[0].pinned)
    }

    @Test
    fun garbageJsonFallsBackToDefault() {
        val layout = DashboardLayout().fromJson("not json {")
        assertEquals(DashboardLayout.default(), layout)
    }

    @Test
    fun visibleEntriesFiltersHidden() {
        val layout = DashboardLayout(
            entries = listOf(
                LayoutEntry(CardId.CPU, visible = false),
                LayoutEntry(CardId.GPU, visible = true),
            ),
        )
        assertEquals(listOf(CardId.GPU), layout.visibleEntries().map { it.card })
    }

    @Test
    fun wideRoundTripsThroughJson() {
        val layout = DashboardLayout(
            entries = listOf(LayoutEntry(CardId.RAM, wide = true), LayoutEntry(CardId.CPU)),
        )
        val restored = DashboardLayout().fromJson(layout.toJson())
        assertTrue(restored.entries.first { it.card == CardId.RAM }.wide)
        assertFalse(restored.entries.first { it.card == CardId.CPU }.wide)
    }

    @Test
    fun jsonWithoutWideDefaultsToFalse() {
        val json = """[{"card":"ram","visible":true,"pinned":false}]"""
        val layout = DashboardLayout().fromJson(json)
        assertFalse(layout.entries.first { it.card == CardId.RAM }.wide)
    }
}