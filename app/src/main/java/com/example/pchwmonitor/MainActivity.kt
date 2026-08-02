package com.example.pchwmonitor

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pchwmonitor.ui.navigation.AppNavHost
import com.example.pchwmonitor.ui.theme.PcHWMonitorTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MonitorViewModel = viewModel()
            val settings by viewModel.settings.collectAsState()
            LaunchedEffect(settings.language) {
                applyLanguage(settings.language)
            }
            PcHWMonitorTheme(themeMode = settings.theme) {
                AppNavHost(viewModel = viewModel)
            }
        }
    }

    private fun applyLanguage(language: String?) {
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val target = language ?: ""
        if (target == current) return
        if (target.isEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(target))
        }
    }
}
