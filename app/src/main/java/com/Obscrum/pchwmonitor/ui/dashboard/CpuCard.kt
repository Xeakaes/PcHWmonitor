package com.Obscrum.pchwmonitor.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.Obscrum.pchwmonitor.domain.model.CpuInfo
import com.Obscrum.pchwmonitor.ui.components.FilledBar
import com.Obscrum.pchwmonitor.ui.components.LineChart
import com.Obscrum.pchwmonitor.ui.components.MetricCard
import com.Obscrum.pchwmonitor.ui.components.RadialGauge
import com.Obscrum.pchwmonitor.ui.components.TemperatureColor

@Composable
fun CpuCard(
    cpu: CpuInfo?,
    labelTemp: String,
    labelUsage: String,
    labelClock: String,
    labelPower: String,
    labelCores: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    MetricCard(title = labelCores, modifier = modifier, compact = compact) {
        val tempColor = TemperatureColor.forTemp(cpu?.tempC ?: 0f)
        val spark = remember { RingBuffer() }
        var points by remember { mutableStateOf(listOf<Float>()) }
        LaunchedEffect(cpu?.tempC) {
            cpu?.tempC?.let {
                spark.append(it)
                points = spark.snapshot()
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp),
        ) {
            RadialGauge(
                value = cpu?.tempC ?: Float.NaN,
                max = 100f,
                color = tempColor,
                label = labelTemp,
                unit = "°C",
                compact = compact,
                modifier = Modifier.weight(1f),
            )
            if (compact) {
                Column(
                    modifier = Modifier.weight(1.2f).padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            StatRow(labelUsage, formatPct(cpu?.usagePct), compact = true)
                            StatRow(labelClock, formatMhz(cpu?.clockMhz), compact = true)
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            StatRow(labelPower, formatPower(cpu?.powerW), compact = true)
                            FilledBar(
                                valuePct = cpu?.usagePct ?: 0f,
                                color = TemperatureColor.forUsage(cpu?.usagePct ?: 0f),
                            )
                        }
                    }
                    CoresStrip(cpu?.loads)
                }
            } else {
                Column(
                    modifier = Modifier.weight(1.2f).padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatRow(labelUsage, formatPct(cpu?.usagePct))
                    StatRow(labelClock, formatMhz(cpu?.clockMhz))
                    StatRow(labelPower, formatPower(cpu?.powerW))
                    FilledBar(
                        valuePct = cpu?.usagePct ?: 0f,
                        color = TemperatureColor.forUsage(cpu?.usagePct ?: 0f),
                    )
                    CoresStrip(cpu?.loads)
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

@Composable
fun CoresStrip(loads: List<Float>?) {
    val cores = loads ?: emptyList()
    if (cores.isEmpty()) {
        Text(text = "--", style = MaterialTheme.typography.labelSmall)
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        cores.forEach { load ->
            FilledBar(
                valuePct = load,
                color = TemperatureColor.forUsage(load),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun StatRow(label: String, value: String, compact: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (compact) 1 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = if (compact) 1 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatPct(v: Float?): String = if (v == null) "--" else "${v.toInt()} %"
private fun formatMhz(v: Float?): String = if (v == null) "--" else "${v.toInt()} MHz"
private fun formatPower(v: Float?): String = if (v == null) "--" else "${v.toInt()} W"
