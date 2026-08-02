package com.Obscrum.pchwmonitor.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [HistorySample::class], version = 1, exportSchema = false)
abstract class HistoryDb : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var instance: HistoryDb? = null

        fun get(context: Context): HistoryDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                HistoryDb::class.java,
                "history.db",
            ).build().also { instance = it }
        }
    }
}
