package com.Obscrum.pchwmonitor.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.Obscrum.pchwmonitor.data.network.ConnectionState
import com.Obscrum.pchwmonitor.domain.model.SystemStatus
import com.Obscrum.pchwmonitor.ui.components.ConnectionBar

@Composable
fun DashboardScreen(
    status: SystemStatus?,
    connection: ConnectionState,
    labelConnecting: String,
    labelConnected: String,
    labelDisconnected: String,
    labelCpu: String,
    labelCpuTemp: String,
    labelUsage: String,
    labelClock: String,
    labelPower: String,
    labelCores: String,
    labelGpuTemp: String,
    labelHotspot: String,
    labelVram: String,
    labelCoreClock: String,
    labelMemClock: String,
    labelIntegratedGpu: String,
    labelRam: String,
    labelRamUsed: String,
    labelNoData: String,
    labelFps: String,
    labelFpsAvg: String,
    labelFpsOnePercentLow: String,
    labelFpsDetails: String,
    labelFpsHint: String,
    labelDisk: String,
    labelDiskRead: String,
    labelDiskWrite: String,
    labelDiskUsage: String,
    labelNet: String,
    labelNetDownload: String,
    labelNetUpload: String,
    labelFan: String,
    layout: DashboardLayout = DashboardLayout.default(),
    onLayoutChange: (DashboardLayout) -> Unit = {},
    editMode: Boolean = false,
    onEditModeChange: (Boolean) -> Unit = {},
    labelMenuHide: String = "",
    labelMenuPin: String = "",
    labelMenuUnpin: String = "",
    labelMenuEdit: String = "",
    labelMenuFpsDetails: String = "",
    labelEditDone: String = "",
    labelEditCancel: String = "",
    labelHiddenCards: String = "",
    modifier: Modifier = Modifier,
    chartWindowSeconds: Int = 60,
    fpsChartMax: Float = 360f,
    diskChartMax: Float = 200f,
    netChartMax: Float = 200f,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val landscape = isLandscapeLayout(maxWidth = maxWidth, maxHeight = maxHeight)
        val sizeClass = sizeClassForWidth(maxWidth)
        val plan = layoutDashboard(layout, sizeClass, maxWidth, landscape)
        var fpsDetailsOpen by rememberSaveable { mutableStateOf(false) }
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ConnectionBar(
                    state = connection,
                    serverName = status?.pc?.name,
                    labelConnecting = labelConnecting,
                    labelConnected = labelConnected,
                    labelDisconnected = labelDisconnected,
                )
            }
            if (status == null || !status.available) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 80.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = labelNoData,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = status?.error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                val rows = buildRows(plan)
                rows.forEach { rowCards ->
                    val visible = rowCards.filter { card -> card.isAvailable(status) }
                    if (visible.isNotEmpty()) {
                        item(key = visible.joinToString { it.name }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                visible.forEach { cardId ->
                                    CardFor(
                                        cardId = cardId,
                                        status = status,
                                        layout = layout,
                                        onLayoutChange = onLayoutChange,
                                        editMode = editMode,
                                        onEditModeChange = onEditModeChange,
                                        labelCpu = labelCpu,
                                        labelCpuTemp = labelCpuTemp,
                                        labelUsage = labelUsage,
                                        labelClock = labelClock,
                                        labelPower = labelPower,
                                        labelGpuTemp = labelGpuTemp,
                                        labelHotspot = labelHotspot,
                                        labelVram = labelVram,
                                        labelCoreClock = labelCoreClock,
                                        labelMemClock = labelMemClock,
                                        labelIntegratedGpu = labelIntegratedGpu,
                                        labelRam = labelRam,
                                        labelRamUsed = labelRamUsed,
                                        labelFps = labelFps,
                                        labelFpsAvg = labelFpsAvg,
                                        labelFpsOnePercentLow = labelFpsOnePercentLow,
                                        labelFpsDetails = labelFpsDetails,
                                        labelDisk = labelDisk,
                                        labelDiskRead = labelDiskRead,
                                        labelDiskWrite = labelDiskWrite,
                                        labelDiskUsage = labelDiskUsage,
                                        labelNet = labelNet,
                                        labelNetDownload = labelNetDownload,
                                        labelNetUpload = labelNetUpload,
                                        labelFan = labelFan,
                                        labelMenuHide = labelMenuHide,
                                        labelMenuPin = labelMenuPin,
                                        labelMenuUnpin = labelMenuUnpin,
                                        labelMenuEdit = labelMenuEdit,
                                        labelMenuFpsDetails = labelMenuFpsDetails,
                                        labelFpsHint = labelFpsHint,
                                        fpsDetailsOpen = fpsDetailsOpen,
                                        onFpsDetailsClick = { fpsDetailsOpen = true },
                                        onFpsDetailsDismiss = { fpsDetailsOpen = false },
                                        chartWindowSeconds = chartWindowSeconds,
                                        fpsChartMax = fpsChartMax,
                                        diskChartMax = diskChartMax,
                                        netChartMax = netChartMax,
                                        compact = plan.isGrid,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

private fun CardId.isAvailable(status: SystemStatus): Boolean = when (this) {
    CardId.CPU, CardId.GPU, CardId.RAM -> true
    CardId.IGPU -> status.igpu != null
    CardId.FPS -> status.fps != null
    CardId.DISK -> status.disk != null
    CardId.NET -> status.net != null
    CardId.FAN -> status.fans != null
}

@Composable
private fun RowScope.CardFor(
    cardId: CardId,
    status: SystemStatus,
    layout: DashboardLayout,
    onLayoutChange: (DashboardLayout) -> Unit,
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    labelCpu: String,
    labelCpuTemp: String,
    labelUsage: String,
    labelClock: String,
    labelPower: String,
    labelGpuTemp: String,
    labelHotspot: String,
    labelVram: String,
    labelCoreClock: String,
    labelMemClock: String,
    labelIntegratedGpu: String,
    labelRam: String,
    labelRamUsed: String,
    labelFps: String,
    labelFpsAvg: String,
    labelFpsOnePercentLow: String,
    labelFpsDetails: String,
    labelDisk: String,
    labelDiskRead: String,
    labelDiskWrite: String,
    labelDiskUsage: String,
    labelNet: String,
    labelNetDownload: String,
    labelNetUpload: String,
    labelFan: String,
    labelMenuHide: String,
    labelMenuPin: String,
    labelMenuUnpin: String,
    labelMenuEdit: String,
    labelMenuFpsDetails: String,
    labelFpsHint: String,
    fpsDetailsOpen: Boolean,
    onFpsDetailsClick: () -> Unit,
    onFpsDetailsDismiss: () -> Unit,
    chartWindowSeconds: Int,
    fpsChartMax: Float,
    diskChartMax: Float,
    netChartMax: Float,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val menu: @Composable RowScope.() -> Unit = {
        if (!editMode) {
            CardMenu(
                cardId = cardId,
                layout = layout,
                onLayoutChange = onLayoutChange,
                onEditModeChange = onEditModeChange,
                labelMenuHide = labelMenuHide,
                labelMenuPin = labelMenuPin,
                labelMenuUnpin = labelMenuUnpin,
                labelMenuEdit = labelMenuEdit,
                labelMenuFpsDetails = labelMenuFpsDetails,
                onFpsDetailsClick = onFpsDetailsClick,
            )
        }
    }
    when (cardId) {
        CardId.CPU -> CpuCard(
            cpu = status.cpu,
            labelTemp = labelCpuTemp,
            labelUsage = labelUsage,
            labelClock = labelClock,
            labelPower = labelPower,
            labelCores = labelCpu,
            compact = compact,
            chartPoints = chartWindowSeconds,
            modifier = modifier,
            menu = menu,
        )
        CardId.GPU -> GpuCard(
            gpu = status.gpu,
            labelTemp = labelGpuTemp,
            labelHotspot = labelHotspot,
            labelUsage = labelUsage,
            labelVram = labelVram,
            labelCoreClock = labelCoreClock,
            labelMemClock = labelMemClock,
            labelPower = labelPower,
            titleFallback = labelGpuTemp,
            compact = compact,
            chartPoints = chartWindowSeconds,
            modifier = modifier,
            menu = menu,
        )
        CardId.IGPU -> status.igpu?.let {
            GpuCard(
                gpu = it,
                labelTemp = labelGpuTemp,
                labelHotspot = labelHotspot,
                labelUsage = labelUsage,
                labelVram = labelVram,
                labelCoreClock = labelCoreClock,
                labelMemClock = labelMemClock,
                labelPower = labelPower,
                titleFallback = labelIntegratedGpu,
                compact = compact,
                chartPoints = chartWindowSeconds,
                modifier = modifier,
                menu = menu,
            )
        }
        CardId.FPS -> FpsCard(
            fps = status.fps,
            labelTitle = labelFps,
            labelAvg = labelFpsAvg,
            labelOnePercentLow = labelFpsOnePercentLow,
            labelFpsDetails = labelFpsDetails,
            labelFpsHint = labelFpsHint,
            compact = compact,
            chartPoints = chartWindowSeconds,
            chartMax = fpsChartMax,
            modifier = modifier,
            menu = menu,
            showDetails = fpsDetailsOpen,
            onDetailsDismiss = onFpsDetailsDismiss,
        )
        CardId.RAM -> RamCard(
            ram = status.ram,
            labelUsage = labelRam,
            labelUsed = labelRamUsed,
            labelClock = labelClock,
            compact = compact,
            chartPoints = chartWindowSeconds,
            modifier = modifier,
            menu = menu,
        )
        CardId.DISK -> DiskCard(
            disk = status.disk,
            labelTitle = labelDisk,
            labelRead = labelDiskRead,
            labelWrite = labelDiskWrite,
            labelUsage = labelDiskUsage,
            compact = compact,
            chartPoints = chartWindowSeconds,
            chartMax = diskChartMax,
            modifier = modifier,
            menu = menu,
        )
        CardId.NET -> NetCard(
            net = status.net,
            labelTitle = labelNet,
            labelDownload = labelNetDownload,
            labelUpload = labelNetUpload,
            compact = compact,
            chartPoints = chartWindowSeconds,
            chartMax = netChartMax,
            modifier = modifier,
            menu = menu,
        )
        CardId.FAN -> FanCard(
            fans = status.fans,
            labelTitle = labelFan,
            compact = compact,
            modifier = modifier,
            menu = menu,
        )
    }
}

@Composable
private fun RowScope.CardMenu(
    cardId: CardId,
    layout: DashboardLayout,
    onLayoutChange: (DashboardLayout) -> Unit,
    onEditModeChange: (Boolean) -> Unit,
    labelMenuHide: String,
    labelMenuPin: String,
    labelMenuUnpin: String,
    labelMenuEdit: String,
    labelMenuFpsDetails: String,
    onFpsDetailsClick: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val isPinned = layout.entries.firstOrNull { it.card == cardId }?.pinned == true
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(labelMenuEdit) },
                onClick = {
                    open = false
                    onEditModeChange(true)
                },
            )
            DropdownMenuItem(
                text = { Text(labelMenuHide) },
                onClick = {
                    open = false
                    onLayoutChange(applyLayoutAction(CardMenuAction.HIDE, layout, cardId))
                },
            )
            DropdownMenuItem(
                text = { Text(if (isPinned) labelMenuUnpin else labelMenuPin) },
                onClick = {
                    open = false
                    onLayoutChange(applyLayoutAction(if (isPinned) CardMenuAction.UNPIN else CardMenuAction.PIN, layout, cardId))
                },
            )
            if (cardId == CardId.FPS && labelMenuFpsDetails.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text(labelMenuFpsDetails) },
                    onClick = {
                        open = false
                        onFpsDetailsClick()
                    },
                )
            }
        }
    }
}