package com.Obscrum.pchwmonitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.Obscrum.pchwmonitor.data.ThemeMode

@Composable
fun PcHWMonitorTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    paletteId: String = "default",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = PaletteDefinitions.schemeFor(paletteId, darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
