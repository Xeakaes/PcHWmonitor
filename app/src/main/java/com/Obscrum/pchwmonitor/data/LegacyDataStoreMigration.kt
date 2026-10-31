package com.Obscrum.pchwmonitor.data

import java.io.File

object LegacyDataStoreMigration {

    /** Moves the pre-1.6.2 DataStore file (filesDir/settings.preferences_pb) to the
     *  location the preferencesDataStore delegate uses (filesDir/datastore/settings.preferences_pb).
     *  No-op when the target already exists (data already migrated or live on the new path). */
    fun migrateLegacyFile(filesDir: File) {
        val legacy = File(filesDir, "settings.preferences_pb")
        val target = File(filesDir, "datastore/settings.preferences_pb")
        if (!legacy.exists()) return
        if (target.exists()) return
        target.parentFile?.mkdirs()
        if (!legacy.renameTo(target)) {
            legacy.copyTo(target, overwrite = false)
            legacy.delete()
        }
    }
}
