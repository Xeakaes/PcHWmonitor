package com.example.pchwmonitor.ui.dashboard

import androidx.compose.ui.unit.Dp

// Landscape detection shared by the dashboard layout branches.
internal fun isLandscapeLayout(maxWidth: Dp, maxHeight: Dp): Boolean = maxWidth > maxHeight
