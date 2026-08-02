package com.Obscrum.pchwmonitor.ui.components

import androidx.compose.ui.graphics.Color
import com.Obscrum.pchwmonitor.ui.theme.TempGreen
import com.Obscrum.pchwmonitor.ui.theme.TempOrange
import com.Obscrum.pchwmonitor.ui.theme.TempRed
import com.Obscrum.pchwmonitor.ui.theme.TempYellow

object TemperatureColor {
    fun forTemp(tempC: Float): Color = when {
        tempC < 60f -> TempGreen
        tempC < 75f -> TempYellow
        tempC < 85f -> TempOrange
        else -> TempRed
    }

    fun forUsage(pct: Float): Color = when {
        pct < 60f -> TempGreen
        pct < 80f -> TempYellow
        pct < 95f -> TempOrange
        else -> TempRed
    }
}
