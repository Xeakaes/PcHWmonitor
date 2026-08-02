package com.Obscrum.pchwmonitor.data.local

import com.Obscrum.pchwmonitor.domain.model.SystemStatus

interface HistoryStore {
    suspend fun record(status: SystemStatus)
    suspend fun history(start: Long): List<HistorySample>
}
