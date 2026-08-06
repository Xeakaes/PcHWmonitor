package com.Obscrum.pchwmonitor.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
    modifier: Modifier = Modifier,
    chartWindowSeconds: Int = 60,
    fpsChartMax: Float = 360f,
    diskChartMax: Float = 200f,
    netChartMax: Float = 200f,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val landscape = isLandscapeLayout(maxWidth = maxWidth, maxHeight = maxHeight)
        if (landscape) {
            LandscapeDashboard(
                status = status,
                connection = connection,
                labelConnecting = labelConnecting,
                labelConnected = labelConnected,
                labelDisconnected = labelDisconnected,
                labelCpu = labelCpu,
                labelCpuTemp = labelCpuTemp,
                labelUsage = labelUsage,
                labelClock = labelClock,
                labelPower = labelPower,
                labelCores = labelCores,
                labelGpuTemp = labelGpuTemp,
                labelHotspot = labelHotspot,
                labelVram = labelVram,
                labelCoreClock = labelCoreClock,
                labelMemClock = labelMemClock,
                labelIntegratedGpu = labelIntegratedGpu,
                labelRam = labelRam,
                labelRamUsed = labelRamUsed,
                labelNoData = labelNoData,
                labelFps = labelFps,
                labelFpsAvg = labelFpsAvg,
                labelFpsOnePercentLow = labelFpsOnePercentLow,
                labelFpsDetails = labelFpsDetails,
                labelFpsHint = labelFpsHint,
                labelDisk = labelDisk,
                labelDiskRead = labelDiskRead,
                labelDiskWrite = labelDiskWrite,
                labelDiskUsage = labelDiskUsage,
                labelNet = labelNet,
                labelNetDownload = labelNetDownload,
                labelNetUpload = labelNetUpload,
                labelFan = labelFan,
                chartWindowSeconds = chartWindowSeconds,
                fpsChartMax = fpsChartMax,
                diskChartMax = diskChartMax,
                netChartMax = netChartMax,
            )
        } else {
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
                    item {
                        CpuCard(
                            cpu = status.cpu,
                            labelTemp = labelCpuTemp,
                            labelUsage = labelUsage,
                            labelClock = labelClock,
                            labelPower = labelPower,
                            labelCores = labelCpu,
                            chartPoints = chartWindowSeconds,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    item {
                        GpuCard(
                            gpu = status.gpu,
                            labelTemp = labelGpuTemp,
                            labelHotspot = labelHotspot,
                            labelUsage = labelUsage,
                            labelVram = labelVram,
                            labelCoreClock = labelCoreClock,
                            labelMemClock = labelMemClock,
                            labelPower = labelPower,
                            chartPoints = chartWindowSeconds,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    if (status.igpu != null) {
                        item {
                            GpuCard(
                                gpu = status.igpu,
                                titleFallback = labelIntegratedGpu,
                                labelTemp = labelGpuTemp,
                                labelHotspot = labelHotspot,
                                labelUsage = labelUsage,
                                labelVram = labelVram,
                                labelCoreClock = labelCoreClock,
                                labelMemClock = labelMemClock,
                                labelPower = labelPower,
                                chartPoints = chartWindowSeconds,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                    if (status.fps != null) {
                        item {
                            FpsCard(
                                fps = status.fps,
                                labelTitle = labelFps,
                                labelAvg = labelFpsAvg,
                                labelOnePercentLow = labelFpsOnePercentLow,
                                labelFpsDetails = labelFpsDetails,
                                labelFpsHint = labelFpsHint,
                                chartPoints = chartWindowSeconds,
                                chartMax = fpsChartMax,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                    if (status.disk != null) {
                        item {
                            DiskCard(
                                disk = status.disk,
                                labelTitle = labelDisk,
                                labelRead = labelDiskRead,
                                labelWrite = labelDiskWrite,
                                labelUsage = labelDiskUsage,
                                chartPoints = chartWindowSeconds,
                                chartMax = diskChartMax,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                    if (status.net != null) {
                        item {
                            NetCard(
                                net = status.net,
                                labelTitle = labelNet,
                                labelDownload = labelNetDownload,
                                labelUpload = labelNetUpload,
                                chartPoints = chartWindowSeconds,
                                chartMax = netChartMax,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                    if (status.fans != null) {
                        item {
                            FanCard(
                                fans = status.fans,
                                labelTitle = labelFan,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                    item {
                        RamCard(
                            ram = status.ram,
                            labelUsage = labelRam,
                            labelUsed = labelRamUsed,
                            labelClock = labelClock,
                            chartPoints = chartWindowSeconds,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun LandscapeDashboard(
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
    chartWindowSeconds: Int = 60,
    fpsChartMax: Float = 360f,
    diskChartMax: Float = 200f,
    netChartMax: Float = 200f,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ConnectionBar(
            state = connection,
            serverName = status?.pc?.name,
            labelConnecting = labelConnecting,
            labelConnected = labelConnected,
            labelDisconnected = labelDisconnected,
        )
        if (status == null || !status.available) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
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
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
            ) {
                item {
                    CpuCard(
                        cpu = status.cpu,
                        labelTemp = labelCpuTemp,
                        labelUsage = labelUsage,
                        labelClock = labelClock,
                        labelPower = labelPower,
                        labelCores = labelCpu,
                        compact = true,
                        chartPoints = chartWindowSeconds,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    GpuCard(
                        gpu = status.gpu,
                        labelTemp = labelGpuTemp,
                        labelHotspot = labelHotspot,
                        labelUsage = labelUsage,
                        labelVram = labelVram,
                        labelCoreClock = labelCoreClock,
                        labelMemClock = labelMemClock,
                        labelPower = labelPower,
                        compact = true,
                        chartPoints = chartWindowSeconds,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (status.igpu != null) {
                    item {
                        GpuCard(
                            gpu = status.igpu,
                            titleFallback = labelIntegratedGpu,
                            labelTemp = labelGpuTemp,
                            labelHotspot = labelHotspot,
                            labelUsage = labelUsage,
                            labelVram = labelVram,
                            labelCoreClock = labelCoreClock,
                            labelMemClock = labelMemClock,
                            labelPower = labelPower,
                            compact = true,
                            chartPoints = chartWindowSeconds,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (status.fps != null) {
                    item {
                        FpsCard(
                            fps = status.fps,
                            labelTitle = labelFps,
                            labelAvg = labelFpsAvg,
                            labelOnePercentLow = labelFpsOnePercentLow,
                            labelFpsDetails = labelFpsDetails,
                            labelFpsHint = labelFpsHint,
                            compact = true,
                            chartPoints = chartWindowSeconds,
                            chartMax = fpsChartMax,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (status.disk != null) {
                    item {
                        DiskCard(
                            disk = status.disk,
                            labelTitle = labelDisk,
                            labelRead = labelDiskRead,
                            labelWrite = labelDiskWrite,
                            labelUsage = labelDiskUsage,
                            compact = true,
                            chartPoints = chartWindowSeconds,
                            chartMax = diskChartMax,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (status.net != null) {
                    item {
                        NetCard(
                            net = status.net,
                            labelTitle = labelNet,
                            labelDownload = labelNetDownload,
                            labelUpload = labelNetUpload,
                            compact = true,
                            chartPoints = chartWindowSeconds,
                            chartMax = netChartMax,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (status.fans != null) {
                    item {
                        FanCard(
                            fans = status.fans,
                            labelTitle = labelFan,
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    RamCard(
                        ram = status.ram,
                        labelUsage = labelRam,
                        labelUsed = labelRamUsed,
                        labelClock = labelClock,
                        compact = true,
                        chartPoints = chartWindowSeconds,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
