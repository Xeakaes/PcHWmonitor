package com.Obscrum.pchwmonitor.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object PaletteDefinitions {
    private val baseIds = listOf("default", "ocean", "ember", "forest", "gold", "midnight", "sunset", "arctic")

    fun idsForApi(apiLevel: Int): List<String> {
        return if (apiLevel >= 31) baseIds + "material_you" else baseIds
    }

    fun schemeFor(
        id: String,
        dark: Boolean,
        dynamicColorScheme: ColorScheme? = null
    ): ColorScheme {
        if (id == "material_you" && dynamicColorScheme != null) {
            return dynamicColorScheme
        }
        return when (id) {
            "ocean" -> if (dark) OceanDark else OceanLight
            "ember" -> if (dark) EmberDark else EmberLight
            "forest" -> if (dark) ForestDark else ForestLight
            "gold" -> if (dark) GoldDark else GoldLight
            "midnight" -> if (dark) MidnightDark else MidnightLight
            "sunset" -> if (dark) SunsetDark else SunsetLight
            "arctic" -> if (dark) ArcticDark else ArcticLight
            else -> if (dark) DarkColorScheme else LightColorScheme
        }
    }
}

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6EA8FF),
    secondary = PurpleGrey80,
    tertiary = Pink80,
    surface = Color(0xFF14141A),
    background = Color(0xFF0E0E13),
)

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    secondary = PurpleGrey40,
    tertiary = Pink40,
)

private val OceanLight = lightColorScheme(
    primary = Color(0xFF1E6FC2),
    secondary = Color(0xFF4A7BA8),
    tertiary = Color(0xFF2C4A66),
    background = Color(0xFFF5F8FB),
    surface = Color(0xFFFFFFFF),
)

private val OceanDark = darkColorScheme(
    primary = Color(0xFF8FC6FF),
    secondary = Color(0xFF7FA8CC),
    tertiary = Color(0xFF9FC6E8),
    background = Color(0xFF0E1319),
    surface = Color(0xFF131A22),
)

private val EmberLight = lightColorScheme(
    primary = Color(0xFFC4501A),
    secondary = Color(0xFFA87A4A),
    tertiary = Color(0xFF7A4A2C),
    background = Color(0xFFFDF7F3),
    surface = Color(0xFFFFFFFF),
)

private val EmberDark = darkColorScheme(
    primary = Color(0xFFFFB089),
    secondary = Color(0xFFCC8F66),
    tertiary = Color(0xFFE8A97A),
    background = Color(0xFF19100C),
    surface = Color(0xFF211510),
)

private val ForestLight = lightColorScheme(
    primary = Color(0xFF26743C),
    secondary = Color(0xFF4A7A5C),
    tertiary = Color(0xFF2C5C4A),
    background = Color(0xFFF5FAF6),
    surface = Color(0xFFFFFFFF),
)

private val ForestDark = darkColorScheme(
    primary = Color(0xFF8FD0A0),
    secondary = Color(0xFF7AA88C),
    tertiary = Color(0xFF9FD0B0),
    background = Color(0xFF0E1510),
    surface = Color(0xFF131A15),
)

private val GoldLight = lightColorScheme(
    primary = Color(0xFF9A7B0F),
    secondary = Color(0xFF8A7A4A),
    tertiary = Color(0xFF6A5C2C),
    background = Color(0xFFFBF9F2),
    surface = Color(0xFFFFFFFF),
)

private val GoldDark = darkColorScheme(
    primary = Color(0xFFE8C34A),
    secondary = Color(0xFFC8A83A),
    tertiary = Color(0xFFE8D08A),
    background = Color(0xFF0E0E0E),
    surface = Color(0xFF141414),
)

private val MidnightLight = lightColorScheme(
    primary = Color(0xFF5B4FCF),
    secondary = Color(0xFF7B6FBF),
    tertiary = Color(0xFF4A3FA0),
    background = Color(0xFFF5F3FA),
    surface = Color(0xFFFFFFFF),
)

private val MidnightDark = darkColorScheme(
    primary = Color(0xFF9B8FFF),
    secondary = Color(0xFF8B7FDF),
    tertiary = Color(0xFFAB9FEF),
    background = Color(0xFF0E0E18),
    surface = Color(0xFF14141F),
)

private val SunsetLight = lightColorScheme(
    primary = Color(0xFFD45D2A),
    secondary = Color(0xFFBF7A4A),
    tertiary = Color(0xFF9A5A3A),
    background = Color(0xFFFFF8F5),
    surface = Color(0xFFFFFFFF),
)

private val SunsetDark = darkColorScheme(
    primary = Color(0xFFFFAA77),
    secondary = Color(0xFFDD8855),
    tertiary = Color(0xFFEEAA77),
    background = Color(0xFF18100C),
    surface = Color(0xFF201510),
)

private val ArcticLight = lightColorScheme(
    primary = Color(0xFF2A8FCF),
    secondary = Color(0xFF4A9FBF),
    tertiary = Color(0xFF3A7FA0),
    background = Color(0xFFF5FAFF),
    surface = Color(0xFFFFFFFF),
)

private val ArcticDark = darkColorScheme(
    primary = Color(0xFF7ACFFF),
    secondary = Color(0xFF6ABFDF),
    tertiary = Color(0xFF8ADFFF),
    background = Color(0xFF0E1518),
    surface = Color(0xFF131A1F),
)