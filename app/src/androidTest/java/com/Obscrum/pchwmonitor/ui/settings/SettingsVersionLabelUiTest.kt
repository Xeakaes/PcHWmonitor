package com.Obscrum.pchwmonitor.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.Obscrum.pchwmonitor.BuildConfig
import com.Obscrum.pchwmonitor.data.AppSettings
import com.Obscrum.pchwmonitor.data.network.ConnectionState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsVersionLabelUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun versionLabelIsDisplayed() {
        compose.setContent {
            MaterialTheme {
                SettingsScreen(
                    settings = AppSettings(),
                    connection = ConnectionState.DISCONNECTED,
                    servers = emptyList(),
                    activeServerId = null,
                    onManageServers = {},
                    backgroundConnectAll = false,
                    onBackgroundConnectAllToggle = {},
                    labelConnecting = "Connecting",
                    labelConnected = "Connected",
                    labelDisconnected = "Disconnected",
                    labelTheme = "Theme",
                    labelThemeSystem = "System",
                    labelThemeLight = "Light",
                    labelThemeDark = "Dark",
                    labelThemePalette = "Palette",
                    paletteLabels = emptyList(),
                    paletteId = "default",
                    onPaletteChange = {},
                    onThemeChange = {},
                    labelLanguage = "Language",
                    labelLanguageSystem = "System",
                    languages = emptyList(),
                    onLanguageChange = {},
                    labelChartWindow = "Chart window",
                    labelChartWindow30s = "30s",
                    labelChartWindow60s = "60s",
                    labelChartWindow300s = "5m",
                    onChartWindowChange = {},
                    labelSupport = "Support",
                    labelSupportDescription = "Support the project",
                    labelSupportPatreon = "Patreon",
                    labelSupportBuyMeACoffee = "Buy me a coffee",
                )
            }
        }

        compose.onNodeWithText("PC HW Monitor v${BuildConfig.VERSION_NAME}")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
