package com.Obscrum.pchwmonitor.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Obscrum.pchwmonitor.ui.theme.GaugeTrackDark
import com.Obscrum.pchwmonitor.ui.theme.GaugeTrackLight
import androidx.compose.foundation.isSystemInDarkTheme
import kotlin.math.min

@Composable
fun RadialGauge(
    value: Float,
    max: Float,
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
    unit: String = "",
    compact: Boolean = false,
) {
    val fraction = remember(max) { { v: Float -> (v / max).coerceIn(0f, 1f) } }
    val animated by animateFloatAsState(
        targetValue = fraction(value),
        animationSpec = tween(durationMillis = 500),
        label = "gauge",
    )
    val track = if (isSystemInDarkTheme()) GaugeTrackDark else GaugeTrackLight
    val sweep = 270f
    val startAngle = 135f

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxWidth(0.72f).height(if (compact) 64.dp else 140.dp)) {
                val stroke = (if (compact) 8.dp else 14.dp).toPx()
                val inset = stroke / 2
                val arcSize = min(size.width, size.height) - inset * 2
                val topLeft = androidx.compose.ui.geometry.Offset(
                    (size.width - arcSize) / 2,
                    (size.height - arcSize) / 2,
                )
                val arc = androidx.compose.ui.geometry.Size(arcSize, arcSize)
                drawArc(
                    color = track,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arc,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweep * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arc,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (value.isNaN()) "--" else "${value.toInt()}",
                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                if (unit.isNotEmpty()) {
                    Text(text = unit, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = if (compact) Modifier.padding(top = 2.dp) else Modifier.padding(top = 4.dp),
        )
    }
}
