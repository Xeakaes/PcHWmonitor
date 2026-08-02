package com.Obscrum.pchwmonitor.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.Obscrum.pchwmonitor.domain.model.GpuInfo
import com.Obscrum.pchwmonitor.ui.components.FilledBar
import com.Obscrum.pchwmonitor.ui.components.LineChart
import com.Obscrum.pchwmonitor.ui.components.MetricCard
import com.Obscrum.pchwmonitor.ui.components.RadialGauge
import com.Obscrum.pchwmonitor.ui.components.TemperatureColor

@Composable
fun GpuCard(
    gpu: GpuInfo?,
    labelTemp: String,
    labelHotspot: String,
    labelUsage: String,
    labelVram: String,
    labelCoreClock: String,
    labelMemClock: String,
    labelPower: String,
    modifier: Modifier = Modifier,
    titleFallback: String = "GPU",
    compact: Boolean = false,
) {
    MetricCard(title = gpu?.name ?: titleFallback, modifier = modifier, compact = compact) {
        val tempColor = TemperatureColor.forTemp(gpu?.tempC ?: 0f)
        val spark = remember { RingBuffer() }
        var points by remember { mutableStateOf(listOf<Float>()) }
        LaunchedEffect(gpu?.tempC) {
            gpu?.tempC?.let {
                spark.append(it)
                points = spark.snapshot()
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp),
        ) {
            RadialGauge(
                value = gpu?.tempC ?: Float.NaN,
                max = 100f,
                color = tempColor,
                label = labelTemp,
                unit = "°C",
                compact = compact,
                modifier = Modifier.weight(1f),
            )
            if (compact) {
                Row(
                    modifier = Modifier.weight(1.2f).padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        StatRow(labelHotspot, formatTemp(gpu?.hotspotC), compact = true)
                        StatRow(labelUsage, formatPct(gpu?.usagePct), compact = true)
                        StatRow(labelVram, formatVram(gpu?.vramUsedMb, gpu?.vramTotalMb), compact = true)
                        FilledBar(
                            valuePct = gpu?.vramUsedMb?.let { v -> gpu.vramTotalMb?.let { t -> if (t > 0f) v / t * 100f else 0f } } ?: 0f,
                            color = TemperatureColor.forUsage(gpu?.usagePct ?: 0f),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        StatRow(labelCoreClock, formatMhz(gpu?.coreClockMhz), compact = true)
                        StatRow(labelMemClock, formatMhz(gpu?.memClockMhz), compact = true)
                        StatRow(labelPower, formatPower(gpu?.powerW), compact = true)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.weight(1.2f).padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatRow(labelHotspot, formatTemp(gpu?.hotspotC))
                    StatRow(labelUsage, formatPct(gpu?.usagePct))
                    StatRow(labelVram, formatVram(gpu?.vramUsedMb, gpu?.vramTotalMb))
                    FilledBar(
                        valuePct = gpu?.vramUsedMb?.let { v -> gpu.vramTotalMb?.let { t -> if (t > 0f) v / t * 100f else 0f } } ?: 0f,
                        color = TemperatureColor.forUsage(gpu?.usagePct ?: 0f),
                    )
                    StatRow(labelCoreClock, formatMhz(gpu?.coreClockMhz))
                    StatRow(labelMemClock, formatMhz(gpu?.memClockMhz))
                    StatRow(labelPower, formatPower(gpu?.powerW))
                }
            }
        }
        if (!compact) {
            LineChart(
                points = points,
                color = tempColor,
                min = 30f,
                max = 100f,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun formatTemp(v: Float?): String = if (v == null) "--" else "${v.toInt()} °C"
private fun formatPct(v: Float?): String = if (v == null) "--" else "${v.toInt()} %"
private fun formatVram(used: Float?, total: Float?): String =
    if (used == null || total == null) "--" else "${(used / 1024).toInt()}/${(total / 1024).toInt()} GB"
private fun formatMhz(v: Float?): String = if (v == null) "--" else "${v.toInt()} MHz"
private fun formatPower(v: Float?): String = if (v == null) "--" else "${v.toInt()} W"
