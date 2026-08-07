package com.Obscrum.pchwmonitor.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.Obscrum.pchwmonitor.MonitorViewModel
import com.Obscrum.pchwmonitor.R
import com.Obscrum.pchwmonitor.ui.dashboard.DashboardScreen
import com.Obscrum.pchwmonitor.ui.history.HistoryMetric
import com.Obscrum.pchwmonitor.ui.history.HistoryScreen
import com.Obscrum.pchwmonitor.ui.settings.SettingsScreen

private data class NavItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val items = listOf(
    NavItem("dashboard", R.string.tab_dashboard, Icons.Filled.Home),
    NavItem("history", R.string.tab_history, Icons.Filled.ShowChart),
    NavItem("settings", R.string.tab_settings, Icons.Filled.Settings),
)

@Composable
fun AppNavHost(viewModel: MonitorViewModel, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val status by viewModel.status.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val chartWindowSeconds by viewModel.chartWindowSeconds.collectAsState()
    val dashboardLayout by viewModel.dashboardLayout.collectAsState()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination
                items.forEach { item ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("dashboard") {
                var editMode by rememberSaveable { mutableStateOf(false) }
                DashboardScreen(
                    status = status,
                    connection = connection,
                    chartWindowSeconds = chartWindowSeconds,
                    layout = dashboardLayout,
                    onLayoutChange = viewModel::setDashboardLayout,
                    editMode = editMode,
                    onEditModeChange = { editMode = it },
                    labelMenuHide = stringResource(R.string.menu_hide),
                    labelMenuPin = stringResource(R.string.menu_pin),
                    labelMenuUnpin = stringResource(R.string.menu_unpin),
                    labelMenuEdit = stringResource(R.string.menu_edit_layout),
                    labelMenuFpsDetails = stringResource(R.string.fps_details_title),
                    labelEditDone = stringResource(R.string.edit_done),
                    labelEditCancel = stringResource(R.string.edit_cancel),
                    labelHiddenCards = stringResource(R.string.hidden_cards),
                    labelConnecting = stringResource(R.string.connecting),
                    labelConnected = stringResource(R.string.connected),
                    labelDisconnected = stringResource(R.string.disconnected),
                    labelCpu = stringResource(R.string.cpu),
                    labelCpuTemp = stringResource(R.string.cpu_temp),
                    labelUsage = stringResource(R.string.usage),
                    labelClock = stringResource(R.string.core_clock),
                    labelPower = stringResource(R.string.power),
                    labelCores = stringResource(R.string.cores),
                    labelGpuTemp = stringResource(R.string.gpu_temp),
                    labelHotspot = stringResource(R.string.gpu_hotspot),
                    labelVram = stringResource(R.string.vram),
                    labelCoreClock = stringResource(R.string.core_clock),
                    labelMemClock = stringResource(R.string.mem_clock),
                    labelIntegratedGpu = stringResource(R.string.label_integrated_gpu),
                    labelRam = stringResource(R.string.ram),
                    labelRamUsed = stringResource(R.string.ram_used),
                    labelNoData = stringResource(R.string.no_data),
                    labelFps = stringResource(R.string.fps_card_title),
                    labelFpsAvg = stringResource(R.string.fps_avg),
                    labelFpsOnePercentLow = stringResource(R.string.fps_1pct_low),
                    labelFpsDetails = stringResource(R.string.fps_details_title),
                    labelFpsHint = stringResource(R.string.fps_hint),
                    labelDisk = stringResource(R.string.disk_card_title),
                    labelDiskRead = stringResource(R.string.disk_read),
                    labelDiskWrite = stringResource(R.string.disk_write),
                    labelDiskUsage = stringResource(R.string.disk_usage),
                    labelNet = stringResource(R.string.net_card_title),
                    labelNetDownload = stringResource(R.string.net_download),
                    labelNetUpload = stringResource(R.string.net_upload),
                    labelFan = stringResource(R.string.fan_card_title),
                )
            }
            composable("history") {
                HistoryScreen(
                    loadSamples = { start -> viewModel.historySamples(start) },
                    metricLabels = mapOf(
                        HistoryMetric.CPU_TEMP to stringResource(R.string.metric_cpu_temp),
                        HistoryMetric.CPU_USAGE to stringResource(R.string.metric_cpu_usage),
                        HistoryMetric.GPU_TEMP to stringResource(R.string.metric_gpu_temp),
                        HistoryMetric.GPU_USAGE to stringResource(R.string.metric_gpu_usage),
                        HistoryMetric.GPU_HOTSPOT to stringResource(R.string.metric_gpu_hotspot),
                        HistoryMetric.RAM_USAGE to stringResource(R.string.metric_ram_usage),
                    ),
                    labelLastHour = stringResource(R.string.last_hour),
                    labelMin = stringResource(R.string.min),
                    labelMax = stringResource(R.string.max),
                    labelNoData = stringResource(R.string.no_data),
                )
            }
            composable("settings") {
                val settings by viewModel.settings.collectAsState()
                SettingsScreen(
                    settings = settings,
                    connection = connection,
                    labelConnecting = stringResource(R.string.connecting),
                    labelConnected = stringResource(R.string.connected),
                    labelDisconnected = stringResource(R.string.disconnected),
                    labelServer = stringResource(R.string.server),
                    labelIp = stringResource(R.string.server_ip),
                    labelPort = stringResource(R.string.port),
                    labelTheme = stringResource(R.string.theme),
                    labelThemeSystem = stringResource(R.string.theme_system),
                    labelThemeLight = stringResource(R.string.theme_light),
                    labelThemeDark = stringResource(R.string.theme_dark),
                    labelLanguage = stringResource(R.string.settings_language),
                    labelLanguageSystem = stringResource(R.string.settings_language_system),
                    languages = listOf(
                        null to stringResource(R.string.settings_language_system),
                        "en" to stringResource(R.string.language_en),
                        "fr" to stringResource(R.string.language_fr),
                        "de" to stringResource(R.string.language_de),
                        "es" to stringResource(R.string.language_es),
                        "it" to stringResource(R.string.language_it),
                        "pt" to stringResource(R.string.language_pt),
                        "pt-BR" to stringResource(R.string.language_pt_br),
                        "ru" to stringResource(R.string.language_ru),
                        "tr" to stringResource(R.string.language_tr),
                        "pl" to stringResource(R.string.language_pl),
                        "nl" to stringResource(R.string.language_nl),
                        "zh" to stringResource(R.string.language_zh),
                        "zh-TW" to stringResource(R.string.language_zh_tw),
                        "ja" to stringResource(R.string.language_ja),
                    ),
                    labelChartWindow = stringResource(R.string.chart_window),
                    labelChartWindow30s = stringResource(R.string.chart_window_30s),
                    labelChartWindow60s = stringResource(R.string.chart_window_60s),
                    labelChartWindow300s = stringResource(R.string.chart_window_300s),
                    labelSave = stringResource(R.string.save),
                    labelSaved = stringResource(R.string.saved),
                    labelSupport = stringResource(R.string.support),
                    labelSupportDescription = stringResource(R.string.support_description),
                    labelSupportPatreon = stringResource(R.string.support_patreon),
                    onSave = { ip, port, theme, language, chartWindowSeconds ->
                        viewModel.saveSettings(ip, port, theme, language, chartWindowSeconds)
                    },
                )
            }
        }
    }
}
