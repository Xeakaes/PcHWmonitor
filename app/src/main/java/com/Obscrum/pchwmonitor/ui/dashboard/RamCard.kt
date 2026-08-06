package com.Obscrum.pchwmonitor.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import com.Obscrum.pchwmonitor.domain.model.RamInfo
import com.Obscrum.pchwmonitor.ui.components.FilledBar
import com.Obscrum.pchwmonitor.ui.components.LineChart
import com.Obscrum.pchwmonitor.ui.components.MetricCard
import com.Obscrum.pchwmonitor.ui.components.TemperatureColor

@Composable
fun RamCard(
    ram: RamInfo?,
    labelUsage: String,
    labelUsed: String,
    labelClock: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    chartPoints: Int = 60,
) {
    MetricCard(title = labelUsage, modifier = modifier, compact = compact) {
        val color = TemperatureColor.forUsage(ram?.usagePct ?: 0f)
        val spark = remember(chartPoints) { RingBuffer(chartPoints) }
        var points by remember { mutableStateOf(listOf<Float>()) }
        LaunchedEffect(ram?.usagePct) {
            ram?.usagePct?.let {
                spark.append(it)
                points = spark.snapshot()
            }
        }
        LaunchedEffect(chartPoints) { spark.clearAndResize(chartPoints) }
        val summary = minAvgMax(points)

        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)) {
            StatRow(
                label = labelUsed,
                value = formatGb(ram?.usedGb, ram?.totalGb),
                compact = compact,
            )
            FilledBar(valuePct = ram?.usagePct ?: 0f, color = color)
            StatRow(labelClock, formatMhz(ram?.clockMhz), compact = compact)
            Text(
                text = "${(ram?.usagePct ?: 0f).toInt()} %",
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
        if (!compact) {
            LineChart(
                points = points,
                color = color,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (summary != null) {
                Text(
                    text = "min ${summary.first.toInt()} / ort. ${summary.second.toInt()} / max ${summary.third.toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun formatGb(used: Float?, total: Float?): String =
    if (used == null || total == null) "--" else "${used.toInt()} / ${total.toInt()} GB"
private fun formatMhz(v: Float?): String = if (v == null) "--" else "${v.toInt()} MHz"
