package com.Obscrum.pchwmonitor.ui.dashboard

enum class CardMenuAction { HIDE, SHOW, PIN, UNPIN }

fun applyLayoutAction(action: CardMenuAction, layout: DashboardLayout, card: CardId): DashboardLayout {
    val result = layout.entries.map { entry ->
        if (entry.card == card) {
            when (action) {
                CardMenuAction.HIDE -> entry.copy(visible = false)
                CardMenuAction.SHOW -> entry.copy(visible = true)
                CardMenuAction.PIN -> entry.copy(pinned = true)
                CardMenuAction.UNPIN -> entry.copy(pinned = false)
            }
        } else {
            entry
        }
    }
    return if (layout.entries.any { it.card == card }) DashboardLayout(result) else layout
}

fun applyReorder(layout: DashboardLayout, from: Int, to: Int): DashboardLayout {
    if (from !in layout.entries.indices || to !in layout.entries.indices) return layout
    val list = layout.entries.toMutableList()
    val entry = list.removeAt(from)
    list.add(to, entry)
    return DashboardLayout(list)
}

fun setCardWidth(layout: DashboardLayout, card: CardId, wide: Boolean): DashboardLayout {
    if (layout.entries.none { it.card == card }) return layout
    return DashboardLayout(layout.entries.map { if (it.card == card) it.copy(wide = wide) else it })
}