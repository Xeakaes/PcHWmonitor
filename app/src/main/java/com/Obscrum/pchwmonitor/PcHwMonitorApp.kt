package com.Obscrum.pchwmonitor

import android.app.Application
import com.Obscrum.pchwmonitor.data.LegacyDataStoreMigration

class PcHwMonitorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LegacyDataStoreMigration.migrateLegacyFile(filesDir)
    }
}
