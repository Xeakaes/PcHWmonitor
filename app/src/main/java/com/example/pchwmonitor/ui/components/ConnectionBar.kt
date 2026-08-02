package com.example.pchwmonitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pchwmonitor.data.network.ConnectionState
import com.example.pchwmonitor.ui.theme.ConnectedGreen
import com.example.pchwmonitor.ui.theme.ErrorRed
import com.example.pchwmonitor.ui.theme.WarningAmber

@Composable
fun ConnectionBar(
    state: ConnectionState,
    serverName: String?,
    labelConnecting: String,
    labelConnected: String,
    labelDisconnected: String,
    modifier: Modifier = Modifier,
) {
    val (color, label) = when (state) {
        ConnectionState.CONNECTED -> ConnectedGreen to labelConnected
        ConnectionState.CONNECTING -> WarningAmber to labelConnecting
        ConnectionState.DISCONNECTED -> ErrorRed to labelDisconnected
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(10.dp)
                    .background(color, CircleShape),
            )
            Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
        if (!serverName.isNullOrBlank()) {
            Text(
                text = serverName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
