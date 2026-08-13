package com.Obscrum.pchwmonitor.ui.dashboard

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.Obscrum.pchwmonitor.data.network.ConnectionState
import com.Obscrum.pchwmonitor.domain.model.CpuInfo
import com.Obscrum.pchwmonitor.domain.model.DiskInfo
import com.Obscrum.pchwmonitor.domain.model.FanInfo
import com.Obscrum.pchwmonitor.domain.model.FpsInfo
import com.Obscrum.pchwmonitor.domain.model.GpuInfo
import com.Obscrum.pchwmonitor.domain.model.NetInfo
import com.Obscrum.pchwmonitor.domain.model.PcInfo
import com.Obscrum.pchwmonitor.domain.model.RamInfo
import com.Obscrum.pchwmonitor.domain.model.SystemStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardScreenUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun fullStatus() = SystemStatus(
        type = "status",
        timestamp = 1754150000L,
        pc = PcInfo(name = "TEST-PC", os = "Windows 11", source = "lhm-lib"),
        cpu = CpuInfo(
            name = "Test CPU",
            usagePct = 34.5f,
            tempC = 61.2f,
            clockMhz = 5100f,
            powerW = 125f,
            loads = listOf(12f, 45f, 33f),
        ),
        gpu = GpuInfo(
            name = "Test GPU",
            usagePct = 78.3f,
            tempC = 71.4f,
            hotspotC = 84.1f,
            vramUsedMb = 6112f,
            vramTotalMb = 12288f,
            coreClockMhz = 2700f,
            memClockMhz = 14000f,
            powerW = 250f,
        ),
        igpu = GpuInfo(name = "Test iGPU", usagePct = 10f, tempC = 50f),
        ram = RamInfo(usedGb = 12f, totalGb = 32f, usagePct = 37.5f, clockMhz = 6000f),
        disk = DiskInfo(usagePct = 40.3f, readMbPerSec = 142.7f, writeMbPerSec = 18.3f),
        net = NetInfo(downloadMbPerSec = 12.4f, uploadMbPerSec = 2.1f),
        fans = listOf(FanInfo(label = "CPU Fan", rpm = 2150f)),
        fps = FpsInfo(name = "Test Game", current = 120f, avg = 115f, onePercentLow = 90f),
    )

    private fun setDashboard(
        status: SystemStatus?,
        editMode: Boolean = false,
        landscape: Boolean = false,
        onLayoutChange: (DashboardLayout) -> Unit = {},
        onEditModeChange: (Boolean) -> Unit = {},
    ) {
        compose.setContent {
            val size = if (landscape) {
                Modifier.size(width = 900.dp, height = 400.dp)
            } else {
                Modifier.size(width = 360.dp, height = 800.dp)
            }
            androidx.compose.material3.MaterialTheme {
                DashboardScreen(
                    status = status,
                    connection = ConnectionState.CONNECTED,
                    labelConnecting = "Connecting",
                    labelConnected = "Connected",
                    labelDisconnected = "Disconnected",
                    labelCpu = "CPU",
                    labelCpuTemp = "Temp",
                    labelUsage = "Usage",
                    labelClock = "Clock",
                    labelPower = "Power",
                    labelCores = "Cores",
                    labelGpuTemp = "GPU",
                    labelHotspot = "Hotspot",
                    labelVram = "VRAM",
                    labelCoreClock = "Core clock",
                    labelMemClock = "Mem clock",
                    labelIntegratedGpu = "iGPU",
                    labelRam = "RAM",
                    labelRamUsed = "Used",
                    labelNoData = "No data",
                    labelFps = "FPS",
                    labelFpsAvg = "Avg",
                    labelFpsOnePercentLow = "1% low",
                    labelFpsDetails = "Details",
                    labelFpsHint = "FPS details",
                    labelDisk = "Disk",
                    labelDiskRead = "Read",
                    labelDiskWrite = "Write",
                    labelDiskUsage = "Usage",
                    labelNet = "Network",
                    labelNetDownload = "Download",
                    labelNetUpload = "Upload",
                    labelFan = "Fans",
                    modifier = size,
                    layout = DashboardLayout.default(),
                    onLayoutChange = onLayoutChange,
                    editMode = editMode,
                    onEditModeChange = onEditModeChange,
                    labelMenuHide = "Hide",
                    labelMenuPin = "Pin",
                    labelMenuUnpin = "Unpin",
                    labelMenuEdit = "Edit",
                    labelMenuFpsDetails = "FPS details",
                    labelEditDone = "Done",
                    labelEditCancel = "Cancel",
                    labelHiddenCards = "Hidden cards",
                    labelCardWidthHalf = "Half width",
                    labelCardWidthFull = "Full width",
                )
            }
        }
    }

    @Test
    fun editModeShowsEditBarAndHidesCard() {
        var changed: DashboardLayout? = null
        setDashboard(status = fullStatus(), editMode = true, onLayoutChange = { changed = it })

        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("CPU").assertIsDisplayed()

        compose.onNodeWithTag("edit-hide-cpu").performClick()

        compose.runOnIdle {
            assertNotNull(changed)
            val entry = changed!!.entries.first { it.card == CardId.CPU }
            assertFalse(entry.visible)
            assertTrue(changed!!.visibleEntries().none { it.card == CardId.CPU })
        }
        compose.onNodeWithText("Hidden cards").assertIsDisplayed()
    }

    @Test
    fun editModeDoneInvokesEditModeChange() {
        var editing = true
        setDashboard(status = fullStatus(), editMode = true, onEditModeChange = { editing = it })

        compose.onNodeWithText("Done").performClick()

        compose.runOnIdle { assertFalse(editing) }
    }

    @Test
    fun landscapeShowsTwoColumnGrid() {
        setDashboard(status = fullStatus(), landscape = true)

        compose.onNodeWithText("CPU").assertIsDisplayed()
        compose.onNodeWithText("GPU").assertIsDisplayed()
    }

    @Test
    fun noDataStateShowsHint() {
        setDashboard(status = null)

        compose.onNodeWithText("No data").assertIsDisplayed()
    }
}