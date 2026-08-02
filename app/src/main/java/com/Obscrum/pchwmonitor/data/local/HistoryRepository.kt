package com.Obscrum.pchwmonitor.data.local

import com.Obscrum.pchwmonitor.domain.model.SystemStatus

class HistoryRepository(
    private val dao: HistoryDao,
    private val retentionMs: Long = 3_600_000L,
) : HistoryStore {
    override suspend fun record(status: SystemStatus) {
        if (!status.available) return
        val now = status.timestamp
        dao.insert(
            HistorySample(
                timestamp = now,
                cpuTempC = status.cpu?.tempC,
                cpuUsagePct = status.cpu?.usagePct,
                gpuTempC = status.gpu?.tempC,
                gpuUsagePct = status.gpu?.usagePct,
                gpuHotspotC = status.gpu?.hotspotC,
                ramUsagePct = status.ram?.usagePct,
            ),
        )
        dao.pruneOlderThan(now - retentionMs)
    }

    override suspend fun history(start: Long): List<HistorySample> = dao.getBetween(start)
}
