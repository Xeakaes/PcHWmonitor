package com.Obscrum.pchwmonitor.ui.dashboard

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Landscape detection shared by the dashboard layout branches.
internal fun isLandscapeLayout(maxWidth: Dp, maxHeight: Dp): Boolean = maxWidth > maxHeight

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
    val columns = if (phoneLandscape) 2 else columnCount(size, maxWidth)
    val isGrid = size == DashboardSizeClass.TABLET || phoneLandscape
    return RenderPlan(columns, firstScreen, rest, isGrid)
}

fun buildRows(plan: RenderPlan): List<List<CardId>> =
    (plan.firstScreen + plan.rest).chunked(plan.columns)