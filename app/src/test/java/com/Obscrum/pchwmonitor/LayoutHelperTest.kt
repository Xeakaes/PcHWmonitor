package com.Obscrum.pchwmonitor

import androidx.compose.ui.unit.dp
import com.Obscrum.pchwmonitor.ui.dashboard.CardId
import com.Obscrum.pchwmonitor.ui.dashboard.DashboardLayout
import com.Obscrum.pchwmonitor.ui.dashboard.DashboardSizeClass
import com.Obscrum.pchwmonitor.ui.dashboard.LayoutEntry
import com.Obscrum.pchwmonitor.ui.dashboard.RenderPlan
import com.Obscrum.pchwmonitor.ui.dashboard.buildRows
import com.Obscrum.pchwmonitor.ui.dashboard.columnCount
import com.Obscrum.pchwmonitor.ui.dashboard.isLandscapeLayout
import com.Obscrum.pchwmonitor.ui.dashboard.layoutDashboard
import com.Obscrum.pchwmonitor.ui.dashboard.sizeClassForWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutHelperTest {
    @Test
    fun wideViewportIsLandscape() {
        assertTrue(isLandscapeLayout(maxWidth = 800.dp, maxHeight = 360.dp))
    }

    @Test
    fun tallViewportIsNotLandscape() {
        assertFalse(isLandscapeLayout(maxWidth = 360.dp, maxHeight = 800.dp))
    }

    @Test
    fun squareViewportIsNotLandscape() {
        assertFalse(isLandscapeLayout(maxWidth = 400.dp, maxHeight = 400.dp))
    }

    @Test
    fun sizeClassBoundaryAt600dp() {
        assertEquals(DashboardSizeClass.PHONE, sizeClassForWidth(599.dp))
        assertEquals(DashboardSizeClass.TABLET, sizeClassForWidth(600.dp))
    }

    @Test
    fun tabletColumnCounts() {
        assertEquals(2, columnCount(DashboardSizeClass.TABLET, 700.dp))
        assertEquals(3, columnCount(DashboardSizeClass.TABLET, 1000.dp))
        assertEquals(4, columnCount(DashboardSizeClass.TABLET, 1300.dp))
    }

    @Test
    fun phonePortraitUsesOneColumnList() {
        val plan = layoutDashboard(
            DashboardLayout.default(), DashboardSizeClass.PHONE, maxWidth = 360.dp, landscape = false,
        )
        assertEquals(1, plan.columns)
        assertTrue(plan.firstScreen.isEmpty())
        assertEquals(8, plan.rest.size)
        assertFalse(plan.isGrid)
    }

    @Test
    fun phoneLandscapePinsPinnedCardsFirst() {
        val plan = layoutDashboard(
            DashboardLayout.default(), DashboardSizeClass.PHONE, maxWidth = 800.dp, landscape = true,
        )
        assertEquals(listOf(CardId.CPU, CardId.GPU, CardId.FPS, CardId.RAM), plan.firstScreen)
        assertEquals(listOf(CardId.IGPU, CardId.DISK, CardId.NET, CardId.FAN), plan.rest)
        assertEquals(2, plan.columns)
        assertTrue(plan.isGrid)
    }

    @Test
    fun hiddenCardsAreExcludedEverywhere() {
        val layout = DashboardLayout(
            entries = DashboardLayout.default().entries.map {
                if (it.card == CardId.FPS || it.card == CardId.NET) it.copy(visible = false) else it
            },
        )
        val plan = layoutDashboard(layout, DashboardSizeClass.PHONE, maxWidth = 800.dp, landscape = true)
        assertEquals(listOf(CardId.CPU, CardId.GPU, CardId.RAM), plan.firstScreen)
        assertEquals(listOf(CardId.IGPU, CardId.DISK, CardId.FAN), plan.rest)
    }

    @Test
    fun tabletIsGridWithoutPinning() {
        val plan = layoutDashboard(
            DashboardLayout.default(), DashboardSizeClass.TABLET, maxWidth = 700.dp, landscape = false,
        )
        assertTrue(plan.firstScreen.isEmpty())
        assertEquals(8, plan.rest.size)
        assertTrue(plan.isGrid)
        assertEquals(2, plan.columns)
    }

    @Test
    fun buildRowsChunksByColumns() {
        val plan = RenderPlan(
            columns = 2,
            firstScreen = listOf(CardId.CPU, CardId.GPU, CardId.FPS, CardId.RAM),
            rest = listOf(CardId.IGPU, CardId.DISK, CardId.NET, CardId.FAN),
            isGrid = true,
        )
        assertEquals(
            listOf(
                listOf(CardId.CPU, CardId.GPU),
                listOf(CardId.FPS, CardId.RAM),
                listOf(CardId.IGPU, CardId.DISK),
                listOf(CardId.NET, CardId.FAN),
            ),
            buildRows(plan, DashboardLayout.default()),
        )
    }

    @Test
    fun buildRowsFlattensFirstScreenThenRest() {
        val plan = RenderPlan(
            columns = 2,
            firstScreen = listOf(CardId.CPU, CardId.GPU),
            rest = listOf(CardId.IGPU, CardId.DISK, CardId.NET),
            isGrid = true,
        )
        assertEquals(
            listOf(
                listOf(CardId.CPU, CardId.GPU),
                listOf(CardId.IGPU, CardId.DISK),
                listOf(CardId.NET),
            ),
            buildRows(plan, DashboardLayout.default()),
        )
    }

    @Test
    fun reorderedLayoutKeepsVisibleOrder() {
        val layout = DashboardLayout(
            entries = listOf(
                LayoutEntry(CardId.CPU, pinned = true),
                LayoutEntry(CardId.RAM, pinned = true),
                LayoutEntry(CardId.GPU, pinned = true),
                LayoutEntry(CardId.FPS, pinned = true),
                LayoutEntry(CardId.IGPU),
                LayoutEntry(CardId.DISK),
                LayoutEntry(CardId.NET),
                LayoutEntry(CardId.FAN),
            ),
        )
        val plan = layoutDashboard(layout, DashboardSizeClass.PHONE, maxWidth = 800.dp, landscape = true)
        assertEquals(listOf(CardId.CPU, CardId.RAM, CardId.GPU, CardId.FPS), plan.firstScreen)
    }

    @Test
    fun wideCardOwnsItsRowInLandscape() {
        val layout = DashboardLayout(
            entries = DashboardLayout.default().entries.map { if (it.card == CardId.RAM) it.copy(wide = true) else it },
        )
        val plan = RenderPlan(
            columns = 2,
            firstScreen = listOf(CardId.CPU, CardId.GPU, CardId.FPS, CardId.RAM),
            rest = listOf(CardId.IGPU, CardId.DISK, CardId.NET, CardId.FAN),
            isGrid = true,
        )
        assertEquals(
            listOf(
                listOf(CardId.CPU, CardId.GPU),
                listOf(CardId.FPS),
                listOf(CardId.RAM),
                listOf(CardId.IGPU, CardId.DISK),
                listOf(CardId.NET, CardId.FAN),
            ),
            buildRows(plan, layout),
        )
    }

    @Test
    fun wideCardSingleColumnBehavesLikeNormal() {
        val layout = DashboardLayout(
            entries = DashboardLayout.default().entries.map { if (it.card == CardId.RAM) it.copy(wide = true) else it },
        )
        val plan = RenderPlan(columns = 1, firstScreen = emptyList(), rest = listOf(CardId.RAM, CardId.CPU), isGrid = false)
        assertEquals(listOf(listOf(CardId.RAM), listOf(CardId.CPU)), buildRows(plan, layout))
    }
}