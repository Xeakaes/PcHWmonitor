package com.Obscrum.pchwmonitor

import com.Obscrum.pchwmonitor.ui.dashboard.CardId
import com.Obscrum.pchwmonitor.ui.dashboard.CardMenuAction
import com.Obscrum.pchwmonitor.ui.dashboard.DashboardLayout
import com.Obscrum.pchwmonitor.ui.dashboard.LayoutEntry
import com.Obscrum.pchwmonitor.ui.dashboard.applyLayoutAction
import com.Obscrum.pchwmonitor.ui.dashboard.applyReorder
import com.Obscrum.pchwmonitor.ui.dashboard.setCardWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardMenuTest {

    @Test
    fun hideMakesCardInvisible() {
        val layout = applyLayoutAction(CardMenuAction.HIDE, DashboardLayout.default(), CardId.FAN)
        assertFalse(layout.entries.first { it.card == CardId.FAN }.visible)
    }

    @Test
    fun showRestoresHiddenCard() {
        var layout = applyLayoutAction(CardMenuAction.HIDE, DashboardLayout.default(), CardId.NET)
        assertFalse(layout.entries.first { it.card == CardId.NET }.visible)
        layout = applyLayoutAction(CardMenuAction.SHOW, layout, CardId.NET)
        assertTrue(layout.entries.first { it.card == CardId.NET }.visible)
    }

    @Test
    fun pinThenUnpinToggles() {
        var layout = applyLayoutAction(CardMenuAction.PIN, DashboardLayout.default(), CardId.IGPU)
        assertTrue(layout.entries.first { it.card == CardId.IGPU }.pinned)
        layout = applyLayoutAction(CardMenuAction.UNPIN, layout, CardId.IGPU)
        assertFalse(layout.entries.first { it.card == CardId.IGPU }.pinned)
    }

    @Test
    fun actionOnEmptyLayoutIsNoop() {
        val layout = DashboardLayout(entries = emptyList())
        assertEquals(layout, applyLayoutAction(CardMenuAction.HIDE, layout, CardId.RAM))
    }

    @Test
    fun reorderMovesEntry() {
        val layout = applyReorder(DashboardLayout.default(), from = 2, to = 5)
        assertEquals(CardId.IGPU, layout.entries[5].card)
        assertEquals(CardId.FPS, layout.entries[2].card)
    }

    @Test
    fun reorderOutOfRangeIsNoop() {
        val layout = DashboardLayout.default()
        assertEquals(layout, applyReorder(layout, from = 0, to = 99))
        assertEquals(layout, applyReorder(layout, from = -1, to = 3))
    }

    @Test
    fun reorderPreservesVisibility() {
        val withHidden = applyLayoutAction(CardMenuAction.HIDE, DashboardLayout.default(), CardId.DISK)
        val reordered = applyReorder(withHidden, from = 0, to = 3)
        assertEquals(CardId.CPU, reordered.entries[3].card)
        assertFalse(reordered.entries.first { it.card == CardId.DISK }.visible)
    }

    @Test
    fun setCardWidthTogglesWide() {
        var layout = setCardWidth(DashboardLayout.default(), CardId.RAM, true)
        assertTrue(layout.entries.first { it.card == CardId.RAM }.wide)
        layout = setCardWidth(layout, CardId.RAM, false)
        assertFalse(layout.entries.first { it.card == CardId.RAM }.wide)
    }

    @Test
    fun setCardWidthOnUnknownCardIsNoop() {
        val layout = DashboardLayout(entries = emptyList())
        assertEquals(layout, setCardWidth(layout, CardId.RAM, true))
    }
}