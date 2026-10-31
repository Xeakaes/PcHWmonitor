package com.Obscrum.pchwmonitor

import com.Obscrum.pchwmonitor.data.LegacyDataStoreMigration
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class LegacyDataStoreMigrationTest {

    private fun tmpDir(): File = createTempDirectory().toFile()

    private fun legacyFile(filesDir: File) = File(filesDir, "settings.preferences_pb")

    private fun targetFile(filesDir: File) = File(filesDir, "datastore/settings.preferences_pb")

    @Test
    fun movesLegacyFileToTargetWhenTargetMissing() {
        val filesDir = tmpDir()
        val content = "legacy-settings".toByteArray()
        legacyFile(filesDir).writeBytes(content)

        LegacyDataStoreMigration.migrateLegacyFile(filesDir)

        assertFalse("legacy file should be gone", legacyFile(filesDir).exists())
        assertTrue("target file should exist", targetFile(filesDir).exists())
        assertArrayEquals(content, targetFile(filesDir).readBytes())
    }

    @Test
    fun targetUntouchedWhenLegacyMissing() {
        val filesDir = tmpDir()
        val content = "new-path-settings".toByteArray()
        targetFile(filesDir).also { it.parentFile?.mkdirs() }.writeBytes(content)

        LegacyDataStoreMigration.migrateLegacyFile(filesDir)

        assertTrue(targetFile(filesDir).exists())
        assertArrayEquals(content, targetFile(filesDir).readBytes())
        assertFalse(legacyFile(filesDir).exists())
    }

    @Test
    fun bothExistTargetNotOverwrittenAndLegacyLeftAsIs() {
        val filesDir = tmpDir()
        val liveContent = "live-new-path-settings".toByteArray()
        val legacyContent = "stale-legacy-settings".toByteArray()
        targetFile(filesDir).also { it.parentFile?.mkdirs() }.writeBytes(liveContent)
        legacyFile(filesDir).writeBytes(legacyContent)

        LegacyDataStoreMigration.migrateLegacyFile(filesDir)

        assertArrayEquals(liveContent, targetFile(filesDir).readBytes())
        assertTrue("legacy file must be left as-is", legacyFile(filesDir).exists())
        assertArrayEquals(legacyContent, legacyFile(filesDir).readBytes())
    }

    @Test
    fun createsNothingWhenNeitherFileExists() {
        val filesDir = tmpDir()

        LegacyDataStoreMigration.migrateLegacyFile(filesDir)

        assertFalse(legacyFile(filesDir).exists())
        assertFalse(targetFile(filesDir).exists())
        assertFalse(
            "datastore dir must not be created when there is nothing to migrate",
            File(filesDir, "datastore").exists(),
        )
    }

    @Test
    fun contentBytesIdenticalAfterMove() {
        val filesDir = tmpDir()
        val content = ByteArray(4096) { (it % 256).toByte() }
        legacyFile(filesDir).writeBytes(content)

        LegacyDataStoreMigration.migrateLegacyFile(filesDir)

        assertArrayEquals(content, targetFile(filesDir).readBytes())
        assertEquals(content.size, targetFile(filesDir).length().toInt())
        assertFalse(legacyFile(filesDir).exists())
    }

    @Test
    fun isIdempotentAcrossRepeatedCalls() {
        val filesDir = tmpDir()
        val content = "legacy-settings".toByteArray()
        legacyFile(filesDir).writeBytes(content)

        LegacyDataStoreMigration.migrateLegacyFile(filesDir)
        LegacyDataStoreMigration.migrateLegacyFile(filesDir)

        assertFalse(legacyFile(filesDir).exists())
        assertArrayEquals(content, targetFile(filesDir).readBytes())
    }
}
