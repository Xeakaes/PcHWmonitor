package com.Obscrum.pchwmonitor.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Obscrum.pchwmonitor.data.local.HistorySample
import com.Obscrum.pchwmonitor.ui.components.LineChart
import com.Obscrum.pchwmonitor.ui.components.TemperatureColor

enum class HistoryMetric(val key: String) {
    CPU_TEMP("cpuTempC"),
    CPU_USAGE("cpuUsagePct"),
    GPU_TEMP("gpuTempC"),
    GPU_USAGE("gpuUsagePct"),
    GPU_HOTSPOT("gpuHotspotC"),
    RAM_USAGE("ramUsagePct"),
}

fun HistorySample.valueFor(metric: HistoryMetric): Float? = when (metric) {
    HistoryMetric.CPU_TEMP -> cpuTempC
    HistoryMetric.CPU_USAGE -> cpuUsagePct
    HistoryMetric.GPU_TEMP -> gpuTempC
    HistoryMetric.GPU_USAGE -> gpuUsagePct
    HistoryMetric.GPU_HOTSPOT -> gpuHotspotC
    HistoryMetric.RAM_USAGE -> ramUsagePct
}

@Composable
fun HistoryScreen(
    loadSamples: suspend (start: Long) -> List<HistorySample>,
    metricLabels: Map<HistoryMetric, String>,
    labelLastHour: String,
    labelMin: String,
    labelMax: String,
    labelNoData: String,
    modifier: Modifier = Modifier,
) {
    var metric by remember { mutableStateOf(HistoryMetric.CPU_TEMP) }
    var samples by remember { mutableStateOf(listOf<HistorySample>()) }

    LaunchedEffect(metric) {
        samples = loadSamples(System.currentTimeMillis() - 3_600_000L)
    }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(HistoryMetric.entries.toList()) { m ->
                FilterChip(
                    selected = m == metric,
                    onClick = { metric = m },
                    label = { Text(metricLabels.getValue(m)) },
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        val values = samples.mapNotNull { it.valueFor(metric) }
        if (values.isEmpty()) {
            Text(
                text = labelNoData,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 60.dp),
            )
            return@Column
        }
        val isTemp = metric in setOf(HistoryMetric.CPU_TEMP, HistoryMetric.GPU_TEMP, HistoryMetric.GPU_HOTSPOT)
        val min = if (isTemp) 30f else 0f
        val max = if (isTemp) 100f else 100f
        val color = if (isTemp) TemperatureColor.forTemp(values.maxOrNull() ?: 0f) else Color(0xFF3B82F6)

        Text(text = labelLastHour, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        LineChart(points = values, color = color, min = min, max = max, modifier = Modifier.padding(top = 8.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "$labelMin ${values.minOrNull()?.toInt() ?: "--"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$labelMax ${values.maxOrNull()?.toInt() ?: "--"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
