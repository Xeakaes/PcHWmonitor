package com.example.pchwmonitor.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.pchwmonitor.ui.theme.GaugeTrackDark
import com.example.pchwmonitor.ui.theme.GaugeTrackLight

@Composable
fun FilledBar(
    valuePct: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = valuePct.coerceIn(0f, 100f) / 100f,
        animationSpec = tween(durationMillis = 500),
        label = "bar",
    )
    val track = if (isSystemInDarkTheme()) GaugeTrackDark else GaugeTrackLight
    Canvas(modifier = modifier.height(10.dp)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(color = track, cornerRadius = radius)
        drawRoundRect(
            color = color,
            cornerRadius = radius,
            size = androidx.compose.ui.geometry.Size(size.width * animated, size.height),
        )
    }
}
