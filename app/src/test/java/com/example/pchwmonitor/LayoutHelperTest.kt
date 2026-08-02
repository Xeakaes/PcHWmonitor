package com.example.pchwmonitor

import androidx.compose.ui.unit.dp
import com.example.pchwmonitor.ui.dashboard.isLandscapeLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutHelperTest {
    @Test
    fun wideViewportIsLandscape() {
        assertTrue(isLandscapeLayout(maxWidth = 800.dp, maxHeight = 360.dp))
    }

    @Test
    fun tallViewportIsNotLandscape() {
        assertFalse(isLandscapeLayout(maxWidth = 360.dp, maxHeight = 800.dp))
    }

    @Test
    fun squareViewportIsNotLandscape() {
        assertFalse(isLandscapeLayout(maxWidth = 400.dp, maxHeight = 400.dp))
    }
}
