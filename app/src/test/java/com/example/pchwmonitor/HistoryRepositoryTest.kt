package com.example.pchwmonitor

import com.example.pchwmonitor.data.local.HistoryDao
import com.example.pchwmonitor.data.local.HistoryRepository
import com.example.pchwmonitor.data.local.HistorySample
import com.example.pchwmonitor.domain.model.SystemStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeHistoryDao : HistoryDao {
    val rows = mutableListOf<HistorySample>()
    val pruned = mutableListOf<Long>()

    override suspend fun insert(sample: HistorySample) {
        rows.removeAll { it.timestamp == sample.timestamp }
        rows.add(sample)
    }

    override suspend fun pruneOlderThan(cutoff: Long) {
        pruned.add(cutoff)
        rows.removeAll { it.timestamp < cutoff }
    }

    override suspend fun getBetween(start: Long): List<HistorySample> =
        rows.filter { it.timestamp >= start }.sortedBy { it.timestamp }
}

class HistoryRepositoryTest {

    private fun status(timestamp: Long, available: Boolean = true) = SystemStatus(
        timestamp = timestamp,
        available = available,
        cpu = com.example.pchwmonitor.domain.model.CpuInfo(tempC = 50f + timestamp / 1000f),
    )

    @Test
    fun recordsOnlyAvailableStatuses() = runTest {
        val dao = FakeHistoryDao()
        val repo = HistoryRepository(dao)
        repo.record(status(1_000_000L))
        repo.record(status(2_000_000L, available = false))
        assertEquals(1, dao.rows.size)
        assertEquals(1_000_000L, dao.rows[0].timestamp)
    }

    @Test
    fun prunesOlderThanRetentionOnRecord() = runTest {
        val dao = FakeHistoryDao()
        val repo = HistoryRepository(dao, retentionMs = 3_600_000L)
        repo.record(status(1_000_000L))
        repo.record(status(2_000_000L))
        repo.record(status(5_000_000L))
        assertTrue(dao.pruned.isNotEmpty())
        assertEquals(5_000_000L - 3_600_000L, dao.pruned.last())
        assertEquals(2, dao.rows.size)
    }

    @Test
    fun historyFiltersByStart() = runTest {
        val dao = FakeHistoryDao()
        val repo = HistoryRepository(dao)
        repo.record(status(100L))
        repo.record(status(200L))
        repo.record(status(300L))
        val result = repo.history(start = 200L)
        assertEquals(listOf(200L, 300L), result.map { it.timestamp })
    }
}
