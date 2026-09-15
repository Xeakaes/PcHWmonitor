package com.Obscrum.pchwmonitor.data.local

import com.Obscrum.pchwmonitor.domain.model.SystemStatus

interface HistoryStore {
    suspend fun record(status: SystemStatus, pcId: String = "default")
    suspend fun history(start: Long, pcId: String = "default"): List<HistorySample>
}
