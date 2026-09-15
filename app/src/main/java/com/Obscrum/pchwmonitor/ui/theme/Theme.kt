package com.Obscrum.pchwmonitor.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.Obscrum.pchwmonitor.data.ThemeMode

@Composable
fun PcHWMonitorTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    paletteId: String = "default",
    customBackgroundEnabled: Boolean = false,
    customBackgroundBitmap: Bitmap? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        customBackgroundEnabled && customBackgroundBitmap != null -> {
            val colors = remember(customBackgroundBitmap) {
                CustomBackgroundManager.extractColors(customBackgroundBitmap)
            }
            if (darkTheme) {
                CustomBackgroundManager.buildDarkScheme(colors)
            } else {
                CustomBackgroundManager.buildLightScheme(colors)
            }
        }
        paletteId == "material_you" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamicScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dynamicScheme
        }
        else -> PaletteDefinitions.schemeFor(paletteId, darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
