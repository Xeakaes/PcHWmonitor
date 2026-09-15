package com.Obscrum.pchwmonitor.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [HistorySample::class], version = 2, exportSchema = true)
abstract class HistoryDb : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history_samples ADD COLUMN pcId TEXT NOT NULL DEFAULT 'default'")
            }
        }

        @Volatile
        private var instance: HistoryDb? = null

        fun get(context: Context): HistoryDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                HistoryDb::class.java,
                "history.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
