package com.Obscrum.pchwmonitor.ui.dashboard

fun minAvgMax(values: List<Float>): Triple<Float, Float, Float>? {
    if (values.isEmpty()) return null
    val min = values.min()
    val max = values.max()
    val avg = values.sum() / values.size
    return Triple(min, avg, max)
}
