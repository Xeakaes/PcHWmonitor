package com.Obscrum.pchwmonitor.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.Obscrum.pchwmonitor.domain.model.FanInfo
import com.Obscrum.pchwmonitor.ui.components.MetricCard

@Composable
fun FanCard(
    fans: List<FanInfo>?,
    labelTitle: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    MetricCard(title = labelTitle, modifier = modifier, compact = compact) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (fans.isNullOrEmpty()) {
                Text("--", style = MaterialTheme.typography.bodyMedium)
            } else {
                fans.forEach { fan ->
                    Text(
                        text = "${fan.label ?: "Fan"}: ${fan.rpm?.toInt() ?: "--"} RPM",
                        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
