package com.Obscrum.pchwmonitor.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [HistorySample::class], version = 3, exportSchema = true)
abstract class HistoryDb : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history_samples ADD COLUMN pcId TEXT NOT NULL DEFAULT 'default'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE history_samples_new (
                        pcId TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        cpuTempC REAL,
                        cpuUsagePct REAL,
                        gpuTempC REAL,
                        gpuUsagePct REAL,
                        gpuHotspotC REAL,
                        ramUsagePct REAL,
                        PRIMARY KEY(pcId, timestamp)
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO history_samples_new SELECT pcId, timestamp, cpuTempC, cpuUsagePct, gpuTempC, gpuUsagePct, gpuHotspotC, ramUsagePct FROM history_samples")
                db.execSQL("DROP TABLE history_samples")
                db.execSQL("ALTER TABLE history_samples_new RENAME TO history_samples")
            }
        }

        @Volatile
        private var instance: HistoryDb? = null

        fun get(context: Context): HistoryDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                HistoryDb::class.java,
                "history.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
