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
import com.Obscrum.pchwmonitor.domain.model.NetInfo
import com.Obscrum.pchwmonitor.ui.components.LineChart
import com.Obscrum.pchwmonitor.ui.components.MetricCard

@Composable
fun NetCard(
    net: NetInfo?,
    labelTitle: String,
    labelDownload: String,
    labelUpload: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    chartPoints: Int = 60,
) {
    MetricCard(title = labelTitle, modifier = modifier, compact = compact) {
        val spark = remember(chartPoints) { RingBuffer(chartPoints) }
        var points by remember { mutableStateOf(listOf<Float>()) }
        LaunchedEffect(net?.downloadMbPerSec, net?.uploadMbPerSec) {
            net?.downloadMbPerSec?.let {
                spark.append(it)
                points = spark.snapshot()
            }
        }
        LaunchedEffect(chartPoints) { spark.clearAndResize(chartPoints) }

        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)) {
            Text(
                text = "${net?.downloadMbPerSec?.toInt() ?: "--"} ↓",
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "$labelDownload ${net?.downloadMbPerSec?.toInt() ?: "--"} · $labelUpload ${net?.uploadMbPerSec?.toInt() ?: "--"} MB/s",
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!compact) {
            LineChart(points = points, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
