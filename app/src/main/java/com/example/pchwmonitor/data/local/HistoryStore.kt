package com.example.pchwmonitor.data.local

import com.example.pchwmonitor.domain.model.SystemStatus

interface HistoryStore {
    suspend fun record(status: SystemStatus)
    suspend fun history(start: Long): List<HistorySample>
}
