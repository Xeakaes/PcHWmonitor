package com.Obscrum.pchwmonitor

import androidx.compose.ui.graphics.Color
import com.Obscrum.pchwmonitor.ui.theme.PaletteDefinitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PaletteTest {

    @Test
    fun defaultVsOceanLightPrimaryDistinct() {
        val defaultPrimary = PaletteDefinitions.schemeFor("default", dark = false).primary
        val oceanPrimary = PaletteDefinitions.schemeFor("ocean", dark = false).primary
        assertNotEquals(defaultPrimary, oceanPrimary)
    }

    @Test
    fun goldDarkBackgroundIsNearlyBlack() {
        val gold = PaletteDefinitions.schemeFor("gold", dark = true)
        assertEquals(Color(0xFF0E0E0E), gold.background)
    }

    @Test
    fun allPalettesProduceSchemesForBothModes() {
        for (id in PaletteDefinitions.ids) {
            val light = PaletteDefinitions.schemeFor(id, dark = false)
            val dark = PaletteDefinitions.schemeFor(id, dark = true)
            assertNotEquals(light.background, dark.background)
        }
    }

    @Test
    fun defaultPaletteReusesExistingColors() {
        val dark = PaletteDefinitions.schemeFor("default", dark = true)
        assertEquals(Color(0xFF6EA8FF), dark.primary)
        val light = PaletteDefinitions.schemeFor("default", dark = false)
        assertEquals(Color(0xFF2563EB), light.primary)
    }

    @Test
    fun unknownIdFallsBackToDefault() {
        assertEquals(
            PaletteDefinitions.schemeFor("default", dark = true),
            PaletteDefinitions.schemeFor("bogus", dark = true),
        )
    }
}