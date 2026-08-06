package com.Obscrum.pchwmonitor.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Obscrum.pchwmonitor.domain.model.FpsInfo
import com.Obscrum.pchwmonitor.ui.components.LineChart
import com.Obscrum.pchwmonitor.ui.components.MetricCard

@Composable
fun FpsCard(
    fps: FpsInfo?,
    labelTitle: String,
    labelAvg: String,
    labelOnePercentLow: String,
    labelFpsDetails: String,
    labelFpsHint: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    chartPoints: Int = 60,
) {
    MetricCard(title = labelTitle, modifier = modifier, compact = compact) {
        val spark = remember(chartPoints) { RingBuffer(chartPoints) }
        var points by remember { mutableStateOf(listOf<Float>()) }
        var showHint by remember { mutableStateOf(false) }
        LaunchedEffect(fps?.current) {
            fps?.current?.let {
                spark.append(it)
                points = spark.snapshot()
            }
        }
        LaunchedEffect(chartPoints) { spark.clearAndResize(chartPoints) }

        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = fps?.current?.toInt()?.toString() ?: "--",
                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showHint = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = labelFpsDetails)
                }
            }
            Text(
                text = "$labelAvg ${fps?.avg?.toInt() ?: "--"} · $labelOnePercentLow ${fps?.onePercentLow?.toInt() ?: "--"} · ${fps?.name ?: "--"}",
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!compact) {
            LineChart(points = points, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }
        if (showHint) {
            AlertDialog(
                onDismissRequest = { showHint = false },
                title = { Text(labelFpsDetails) },
                text = { Text(labelFpsHint) },
                confirmButton = {
                    TextButton(onClick = { showHint = false }) { Text("OK") }
                },
            )
        }
    }
}
