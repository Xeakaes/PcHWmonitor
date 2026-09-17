package com.Obscrum.pchwmonitor.data.local

import androidx.room.Entity

@Entity(
    tableName = "history_samples",
    primaryKeys = ["pcId", "timestamp"]
)
data class HistorySample(
    val pcId: String,
    val timestamp: Long,
    val cpuTempC: Float? = null,
    val cpuUsagePct: Float? = null,
    val gpuTempC: Float? = null,
    val gpuUsagePct: Float? = null,
    val gpuHotspotC: Float? = null,
    val ramUsagePct: Float? = null,
)
