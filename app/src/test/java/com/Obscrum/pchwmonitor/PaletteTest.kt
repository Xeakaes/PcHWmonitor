package com.Obscrum.pchwmonitor

import androidx.compose.ui.graphics.Color
import com.Obscrum.pchwmonitor.ui.theme.PaletteDefinitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
        for (id in PaletteDefinitions.idsForApi(31)) {
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

    @Test
    fun swatchColorUsesLightPrimary() {
        assertEquals(Color(0xFF2563EB), PaletteDefinitions.swatchColor("default"))
        assertEquals(Color(0xFF1E6FC2), PaletteDefinitions.swatchColor("ocean"))
    }

    @Test
    fun swatchColorMaterialYouIsDistinctFromDefault() {
        val materialYou = PaletteDefinitions.swatchColor("material_you")
        assertEquals(Color(0xFF6750A4), materialYou)
        assertNotEquals(PaletteDefinitions.swatchColor("default"), materialYou)
    }

    @Test
    fun swatchColorUnknownFallsBackToDefault() {
        assertEquals(
            PaletteDefinitions.swatchColor("default"),
            PaletteDefinitions.swatchColor("bogus"),
        )
    }

    @Test
    fun swatchColorEveryPaletteIsDistinct() {
        val seen = mutableSetOf<Color>()
        for (id in PaletteDefinitions.idsForApi(31)) {
            assertTrue("duplicate swatch for $id", seen.add(PaletteDefinitions.swatchColor(id)))
        }
    }
}