package com.Obscrum.pchwmonitor.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
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
import com.Obscrum.pchwmonitor.domain.model.DiskInfo
import com.Obscrum.pchwmonitor.ui.components.FilledBar
import com.Obscrum.pchwmonitor.ui.components.LineChart
import com.Obscrum.pchwmonitor.ui.components.MetricCard
import com.Obscrum.pchwmonitor.ui.components.TemperatureColor

@Composable
fun DiskCard(
    disk: DiskInfo?,
    labelTitle: String,
    labelRead: String,
    labelWrite: String,
    labelUsage: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    chartPoints: Int = 60,
    chartMax: Float = 200f,
    menu: @Composable RowScope.() -> Unit = {},
) {
    MetricCard(title = labelTitle, modifier = modifier, compact = compact, menu = menu) {
        val spark = remember(chartPoints) { RingBuffer(chartPoints) }
        var points by remember { mutableStateOf(listOf<Float>()) }
        LaunchedEffect(disk?.readMbPerSec, disk?.writeMbPerSec) {
            disk?.readMbPerSec?.let {
                spark.append(it + (disk.writeMbPerSec ?: 0f))
                points = spark.downsample(CHART_MAX_POINTS)
            }
        }
        LaunchedEffect(chartPoints) { spark.clearAndResize(chartPoints) }

        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)) {
            Text(
                text = labelUsage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${(disk?.usagePct ?: 0f).toInt()} %",
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TemperatureColor.forUsage(disk?.usagePct ?: 0f),
            )
            FilledBar(valuePct = disk?.usagePct ?: 0f, color = TemperatureColor.forUsage(disk?.usagePct ?: 0f))
            Text(
                text = "$labelRead ${disk?.readMbPerSec?.toInt() ?: "--"} MB/s · $labelWrite ${disk?.writeMbPerSec?.toInt() ?: "--"} MB/s",
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!compact) {
            LineChart(points = points, color = MaterialTheme.colorScheme.primary, max = chartMax, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
