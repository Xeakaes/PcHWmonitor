package com.example.pchwmonitor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_samples")
data class HistorySample(
    @PrimaryKey val timestamp: Long,
    val cpuTempC: Float? = null,
    val cpuUsagePct: Float? = null,
    val gpuTempC: Float? = null,
    val gpuUsagePct: Float? = null,
    val gpuHotspotC: Float? = null,
    val ramUsagePct: Float? = null,
)
