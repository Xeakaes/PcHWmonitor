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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalContext
import com.Obscrum.pchwmonitor.data.ServerConfig
import com.Obscrum.pchwmonitor.util.PATREON_URL
import com.Obscrum.pchwmonitor.util.QrPayload
import java.util.UUID
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
    // Custom Background parameters
    labelCustomBackground: String = "Custom Background",
    labelCustomBackgroundDescription: String = "Set a background image and auto-extract theme colors",
    labelCustomBackgroundEnable: String = "Enable custom background",
    labelCustomBackgroundPick: String = "Choose image",
    labelCustomBackgroundRemove: String = "Remove background",
    labelCustomBackgroundBlur: String = "Glassmorphism cards",
    labelCustomBackgroundBlurDescription: String = "Cards become semi-transparent with blur effect",
    customBackgroundEnabled: Boolean = false,
    glassmorphismEnabled: Boolean = false,
    customBackgroundUri: String? = null,
    onCustomBackgroundToggle: (Boolean) -> Unit = {},
    onGlassmorphismToggle: (Boolean) -> Unit = {},
    onCustomBackgroundPick: (String) -> Unit = {},
    onCustomBackgroundRemove: () -> Unit = {},
    // Multi-PC parameters
    labelSavedServers: String = "Saved Servers",
    labelSelect: String = "Select",
    labelDelete: String = "Delete",
    labelAddServer: String = "Add new server",
    savedServers: List<ServerConfig> = emptyList(),
    activeServerId: String? = null,
    onSelectSavedServer: (String) -> Unit = {},
    onDeleteSavedServer: (String) -> Unit = {},
    onAddServer: (ServerConfig) -> Unit = {},
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
    var customBackgroundEnabled by remember { mutableStateOf(settings.customBackgroundEnabled) }
    var glassmorphismEnabled by remember { mutableStateOf(settings.customBackgroundEnabled) }
    var currentCustomBackgroundUri by remember { mutableStateOf(settings.customBackgroundUri) }
    var showAddServerForm by remember { mutableStateOf(false) }
    var newServerName by remember { mutableStateOf("") }
    var newServerIp by remember { mutableStateOf("") }
    var newServerPort by remember { mutableStateOf("8765") }
    var newServerToken by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val uriString = it.toString()
            currentCustomBackgroundUri = uriString
            onCustomBackgroundPick(uriString)
        }
    }

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

        // Server List Section (Multi-PC)
        Text(
            text = labelSavedServers,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Show saved servers
        savedServers.forEach { server ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Active indicator
                if (server.id == activeServerId) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (server.id == activeServerId) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        text = "${server.ip}:${server.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Select button
                    if (server.id != activeServerId) {
                        TextButton(onClick = { onSelectSavedServer(server.id) }) {
                            Text(labelSelect)
                        }
                    }
                    // Delete button
                    IconButton(onClick = { onDeleteSavedServer(server.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = labelDelete)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Add new server button
        OutlinedButton(
            onClick = { showAddServerForm = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(labelAddServer)
        }

        // Add server dialog
        if (showAddServerForm) {
            AlertDialog(
                onDismissRequest = { showAddServerForm = false },
                title = { Text(labelAddServer) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = newServerName,
                            onValueChange = { newServerName = it },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = newServerIp,
                            onValueChange = { newServerIp = it },
                            label = { Text("IP Address") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = newServerPort,
                            onValueChange = { newServerPort = it },
                            label = { Text("Port") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = newServerToken,
                            onValueChange = { newServerToken = it },
                            label = { Text("Token (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val portInt = newServerPort.toIntOrNull() ?: 8765
                            val server = ServerConfig(
                                id = UUID.randomUUID().toString(),
                                name = newServerName.ifBlank { "PC" },
                                ip = newServerIp.trim(),
                                port = portInt,
                                token = newServerToken.trim().ifBlank { null },
                            )
                            onAddServer(server)
                            showAddServerForm = false
                            newServerName = ""
                            newServerIp = ""
                            newServerPort = "8765"
                            newServerToken = ""
                        },
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    Button(onClick = { showAddServerForm = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

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

        // Custom Background Section
        Text(
            text = labelCustomBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = labelCustomBackgroundDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Enable toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = labelCustomBackgroundEnable,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.Switch(
                checked = customBackgroundEnabled,
                onCheckedChange = {
                    customBackgroundEnabled = it
                    onCustomBackgroundToggle(it)
                },
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Glassmorphism toggle (only when custom background enabled)
        if (customBackgroundEnabled) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = labelCustomBackgroundBlur,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.Switch(
                    checked = glassmorphismEnabled,
                    onCheckedChange = {
                        glassmorphismEnabled = it
                        onGlassmorphismToggle(it)
                    },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = labelCustomBackgroundBlurDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Pick image button
        OutlinedButton(
            onClick = { imagePicker.launch("image/*") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(labelCustomBackgroundPick)
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Remove background button (if image is set)
        if (currentCustomBackgroundUri != null) {
            OutlinedButton(
                onClick = {
                    currentCustomBackgroundUri = null
                    onCustomBackgroundRemove()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(labelCustomBackgroundRemove)
            }
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
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, PATREON_URL.toUri()))
                } catch (_: android.content.ActivityNotFoundException) {
                    // No browser available
                }
            },
        ) {
            Text(labelSupportPatreon)
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
