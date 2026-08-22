package com.Obscrum.pchwmonitor.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalContext
import com.Obscrum.pchwmonitor.util.PATREON_URL
import com.Obscrum.pchwmonitor.util.QrPayload
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.Obscrum.pchwmonitor.data.AppSettings
import com.Obscrum.pchwmonitor.data.ThemeMode
import com.Obscrum.pchwmonitor.data.network.ConnectionState
import com.Obscrum.pchwmonitor.ui.components.ConnectionBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    connection: ConnectionState,
    labelConnecting: String,
    labelConnected: String,
    labelDisconnected: String,
    labelServer: String,
    labelIp: String,
    labelPort: String,
    labelToken: String,
    labelTheme: String,
    labelThemeSystem: String,
    labelThemeLight: String,
    labelThemeDark: String,
    labelThemePalette: String,
    paletteLabels: List<Pair<String, String>>,
    paletteId: String,
    onPaletteChange: (String) -> Unit,
    labelLanguage: String,
    labelLanguageSystem: String,
    languages: List<Pair<String?, String>>,
    labelChartWindow: String,
    labelChartWindow30s: String,
    labelChartWindow60s: String,
    labelChartWindow300s: String,
    labelSave: String,
    labelSaved: String,
    labelSupport: String,
    labelSupportDescription: String,
    labelSupportPatreon: String,
    labelDiscover: String = "Discover",
    labelDiscovering: String = "Scanning...",
    labelNoServers: String = "No servers found",
    labelConnectionMethod: String = "Connection method",
    labelMethodManual: String = "Manual (IP + port)",
    labelMethodScan: String = "Scan network",
    labelConnect: String = "Connect",
    labelQrScan: String = "Fill via QR",
    discoveredServers: List<Triple<String, String, Int>> = emptyList(),
    isScanning: Boolean = false,
    onDiscover: () -> Unit = {},
    onServerSelected: (ip: String, port: Int) -> Unit = { _, _ -> },
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
    onSave: (ip: String, port: Int, authToken: String?, theme: ThemeMode, language: String?, chartWindowSeconds: Int) -> Unit,
) {
    val context = LocalContext.current
    var ip by remember { mutableStateOf(settings.serverIp) }
    var port by remember { mutableStateOf(settings.serverPort.toString()) }
    var authToken by remember { mutableStateOf(settings.authToken ?: "") }
    var theme by remember { mutableStateOf(settings.theme) }
    var language by remember { mutableStateOf(settings.language) }
    var chartWindowSeconds by remember { mutableIntStateOf(settings.chartWindowSeconds) }
    var saved by remember { mutableStateOf(false) }
    var scanMode by rememberSaveable { mutableStateOf(false) }

    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { text ->
            QrPayload.parse(text)?.let { (qrIp, qrPort, qrToken) ->
                ip = qrIp
                port = qrPort.toString()
                authToken = qrToken
                scanMode = false
                saved = false
            }
        }
    }

    fun connectNow() {
        val portInt = port.toIntOrNull() ?: 8765
        saved = true
        onSave(ip.trim(), portInt, authToken.trim().ifBlank { null }, theme, language, chartWindowSeconds)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        ConnectionBar(
            state = connection,
            serverName = null,
            labelConnecting = labelConnecting,
            labelConnected = labelConnected,
            labelDisconnected = labelDisconnected,
        )
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = labelServer,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Connection method picker
        var methodExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = methodExpanded,
            onExpandedChange = { methodExpanded = it },
        ) {
            OutlinedTextField(
                value = if (scanMode) labelMethodScan else labelMethodManual,
                onValueChange = {},
                readOnly = true,
                label = { Text(labelConnectionMethod) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = methodExpanded, onDismissRequest = { methodExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(labelMethodManual) },
                    onClick = {
                        scanMode = false
                        methodExpanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(labelMethodScan) },
                    onClick = {
                        scanMode = true
                        methodExpanded = false
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Fill all connection fields from the server's QR code
        OutlinedButton(
            onClick = {
                qrLauncher.launch(ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    setPrompt(labelQrScan)
                    setBeepEnabled(false)
                    setOrientationLocked(true)
                })
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(labelQrScan)
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (!scanMode) {
            // Manual entry: IP + port
            OutlinedTextField(
                value = ip,
                onValueChange = {
                    ip = it
                    saved = false
                },
                label = { Text(labelIp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = port,
                onValueChange = {
                    port = it.filter { ch -> ch.isDigit() }.take(5)
                    saved = false
                },
                label = { Text(labelPort) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            // Network scan
            Button(
                onClick = onDiscover,
                enabled = !isScanning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isScanning) labelDiscovering else labelDiscover)
            }
            if (discoveredServers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                discoveredServers.forEach { (name, serverIp, serverPort) ->
                    Button(
                        onClick = {
                            ip = serverIp
                            port = serverPort.toString()
                            saved = false
                            scanMode = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("$name ($serverIp:$serverPort)")
                    }
                }
            } else if (!isScanning) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = labelNoServers,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Access token is required by every connection path
        OutlinedTextField(
            value = authToken,
            onValueChange = {
                authToken = it
                saved = false
            },
            label = { Text(labelToken) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Primary action: apply settings and connect immediately
        Button(
            onClick = { connectNow() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(labelConnect)
        }
        if (saved) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = labelSaved,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = labelTheme,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        listOf(
            ThemeMode.SYSTEM to labelThemeSystem,
            ThemeMode.LIGHT to labelThemeLight,
            ThemeMode.DARK to labelThemeDark,
        ).forEach { (mode, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = theme == mode, onClick = { theme = mode })
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = labelThemePalette,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        paletteLabels.forEach { (id, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = paletteId == id, onClick = { onPaletteChange(id) })
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = labelChartWindow,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        listOf(
            30 to labelChartWindow30s,
            60 to labelChartWindow60s,
            300 to labelChartWindow300s,
        ).forEach { (seconds, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = chartWindowSeconds == seconds, onClick = { chartWindowSeconds = seconds })
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = labelLanguage,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = languages.firstOrNull { it.first == language }?.second ?: labelLanguageSystem,
                onValueChange = {},
                readOnly = true,
                label = { Text(labelLanguage) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                languages.forEach { (code, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            language = code
                            expanded = false
                            saved = false
                        },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    val portInt = port.toIntOrNull() ?: 8765
                    saved = true
                    onSave(ip.trim(), portInt, authToken.trim().ifBlank { null }, theme, language, chartWindowSeconds)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(labelSave)
            }
        }
        if (saved) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = labelSaved,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = labelSupport,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = labelSupportDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, PATREON_URL.toUri()))
            },
        ) {
            Text(labelSupportPatreon)
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
