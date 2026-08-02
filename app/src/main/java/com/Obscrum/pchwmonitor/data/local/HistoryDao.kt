package com.Obscrum.pchwmonitor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(sample: HistorySample)

    @Query("DELETE FROM history_samples WHERE timestamp < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long)

    @Query("SELECT * FROM history_samples WHERE timestamp >= :start ORDER BY timestamp ASC")
    suspend fun getBetween(start: Long): List<HistorySample>
}
