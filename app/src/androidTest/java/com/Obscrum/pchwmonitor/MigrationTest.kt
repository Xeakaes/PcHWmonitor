package com.Obscrum.pchwmonitor

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.Obscrum.pchwmonitor.data.local.HistoryDb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HistoryDb::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun createV1FromExportedSchemaAndReadBack() {
        val db: androidx.sqlite.db.SupportSQLiteDatabase = helper.createDatabase("migration-test", 1)
        try {
            db.execSQL(
                "INSERT INTO history_samples (timestamp, cpuTempC, cpuUsagePct, gpuTempC, gpuUsagePct, gpuHotspotC, ramUsagePct) " +
                    "VALUES (1000, 55.0, 30.0, 60.0, 40.0, 75.0, 50.0)",
            )
            val cursor = db.query("SELECT * FROM history_samples")
            try {
                assertTrue(cursor.moveToFirst())
                assertEquals(1000L, cursor.getLong(0))
                assertEquals(55.0f, cursor.getFloat(1))
                assertEquals(75.0f, cursor.getFloat(5))
                assertEquals(1, cursor.count)
            } finally {
                cursor.close()
            }
        } finally {
            db.close()
        }
    }
}
