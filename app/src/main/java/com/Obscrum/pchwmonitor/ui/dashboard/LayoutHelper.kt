package com.Obscrum.pchwmonitor.ui.dashboard

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Landscape detection shared by the dashboard layout branches.
internal fun isLandscapeLayout(maxWidth: Dp, maxHeight: Dp): Boolean = maxWidth > maxHeight

// Upper bound of points drawn per sparkline; LTTB downsampling preserves the shape.
internal const val CHART_MAX_POINTS = 240

enum class DashboardSizeClass { PHONE, TABLET }

fun sizeClassForWidth(maxWidth: Dp): DashboardSizeClass =
    if (maxWidth >= 600.dp) DashboardSizeClass.TABLET else DashboardSizeClass.PHONE

fun columnCount(size: DashboardSizeClass, maxWidth: Dp): Int = when (size) {
    DashboardSizeClass.PHONE -> 1
    DashboardSizeClass.TABLET -> when {
        maxWidth < 1000.dp -> 2
        maxWidth < 1300.dp -> 3
        else -> 4
    }
}

data class RenderPlan(
    val columns: Int,
    val firstScreen: List<CardId>,
    val rest: List<CardId>,
    val isGrid: Boolean,
)

fun layoutDashboard(
    layout: DashboardLayout,
    size: DashboardSizeClass,
    maxWidth: Dp,
    landscape: Boolean,
    maxHeight: Dp = Dp.Infinity,
): RenderPlan {
    val visible = layout.visibleEntries()
    val phoneLandscape = size == DashboardSizeClass.PHONE && landscape
    val firstScreen = if (phoneLandscape) {
        visible.filter { it.pinned }.map { it.card }
    } else {
        emptyList()
    }
    val rest = if (phoneLandscape) {
        visible.filterNot { it.pinned }.map { it.card }
    } else {
        visible.map { it.card }
    }
    // Short landscape screens (case-embedded kiosk displays) fit more cards
    // per row so the first screen shows everything without scrolling.
    val columns = if (phoneLandscape) phoneLandscapeColumns(maxHeight) else columnCount(size, maxWidth)
    val isGrid = size == DashboardSizeClass.TABLET || phoneLandscape
    return RenderPlan(columns, firstScreen, rest, isGrid)
}

private fun phoneLandscapeColumns(maxHeight: Dp): Int = when {
    maxHeight < 400.dp -> 4
    maxHeight < 550.dp -> 3
    else -> 2
}

fun buildRows(plan: RenderPlan, layout: DashboardLayout): List<List<CardId>> {
    val wideCards = layout.entries.filter { it.wide }.map { it.card }.toSet()
    val rows = mutableListOf<List<CardId>>()
    var pending = mutableListOf<CardId>()
    for (card in plan.firstScreen + plan.rest) {
        if (card in wideCards) {
            if (pending.isNotEmpty()) { rows.add(pending.toList()); pending = mutableListOf() }
            rows.add(listOf(card))
        } else {
            pending.add(card)
            if (pending.size >= plan.columns) { rows.add(pending.toList()); pending = mutableListOf() }
        }
    }
    if (pending.isNotEmpty()) rows.add(pending.toList())
    return rows
}