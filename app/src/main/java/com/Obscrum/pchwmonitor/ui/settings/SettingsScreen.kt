package com.Obscrum.pchwmonitor.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.Obscrum.pchwmonitor.R
import com.Obscrum.pchwmonitor.data.AppSettings
import com.Obscrum.pchwmonitor.data.ServerConfig
import com.Obscrum.pchwmonitor.data.ThemeMode
import com.Obscrum.pchwmonitor.data.network.ConnectionState
import com.Obscrum.pchwmonitor.ui.components.ConnectionBar
import com.Obscrum.pchwmonitor.ui.theme.PaletteDefinitions
import com.Obscrum.pchwmonitor.util.PATREON_URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    connection: ConnectionState,
    servers: List<ServerConfig>,
    activeServerId: String?,
    onManageServers: () -> Unit,
    backgroundConnectAll: Boolean,
    onBackgroundConnectAllToggle: (Boolean) -> Unit,
    labelConnecting: String,
    labelConnected: String,
    labelDisconnected: String,
    labelTheme: String,
    labelThemeSystem: String,
    labelThemeLight: String,
    labelThemeDark: String,
    labelThemePalette: String,
    paletteLabels: List<Pair<String, String>>,
    paletteId: String,
    onPaletteChange: (String) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    labelLanguage: String,
    labelLanguageSystem: String,
    languages: List<Pair<String?, String>>,
    onLanguageChange: (String?) -> Unit,
    labelChartWindow: String,
    labelChartWindow30s: String,
    labelChartWindow60s: String,
    labelChartWindow300s: String,
    onChartWindowChange: (Int) -> Unit,
    labelSupport: String,
    labelSupportDescription: String,
    labelSupportPatreon: String,
    modifier: Modifier = Modifier,
    // Custom Background parameters
    labelCustomBackground: String = "Custom Background",
    labelCustomBackgroundDescription: String = "Set a background image and auto-extract theme colors",
    labelCustomBackgroundEnable: String = "Enable custom background",
    labelCustomBackgroundPick: String = "Choose image",
    labelCustomBackgroundRemove: String = "Remove background",
    labelCustomBackgroundBlur: String = "Glassmorphism cards",
    labelCustomBackgroundBlurDescription: String = "Cards become semi-transparent with blur effect",
    customBackgroundEnabled: Boolean = false,
    glassmorphism: Boolean = false,
    customBackgroundUri: String? = null,
    onCustomBackgroundToggle: (Boolean) -> Unit = {},
    onGlassmorphismToggle: (Boolean) -> Unit = {},
    onCustomBackgroundPick: (String) -> Unit = {},
    onCustomBackgroundRemove: () -> Unit = {},
) {
    val context = LocalContext.current
    val activeServer = servers.find { it.id == activeServerId }
    val defaultServerName = stringResource(R.string.default_server_name)

    val imagePicker = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onCustomBackgroundPick(it.toString()) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Connection summary card: active server name + status + Manage
        Text(
            text = stringResource(R.string.connection_card_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ConnectionBar(
            state = connection,
            serverName = activeServer?.name?.ifBlank { defaultServerName },
            labelConnecting = labelConnecting,
            labelConnected = labelConnected,
            labelDisconnected = labelDisconnected,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onManageServers,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.manage_servers))
        }
        Spacer(modifier = Modifier.height(20.dp))

        // Background connect-all switch
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.background_connect_all_label),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.background_connect_all_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = backgroundConnectAll,
                onCheckedChange = onBackgroundConnectAllToggle,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = labelTheme,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(
                ThemeMode.SYSTEM to labelThemeSystem,
                ThemeMode.LIGHT to labelThemeLight,
                ThemeMode.DARK to labelThemeDark,
            ).forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = settings.theme == mode,
                    onClick = { onThemeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = labelThemePalette,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            paletteLabels.forEach { (id, label) ->
                val selected = paletteId == id
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .semantics { contentDescription = label }
                        .clip(CircleShape)
                        .clickable { onPaletteChange(id) }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            drawCircle(color = PaletteDefinitions.swatchColor(id))
                            PaletteDefinitions.swatchAccent(id)?.let { accent ->
                                drawArc(
                                    color = accent,
                                    startAngle = 315f,
                                    sweepAngle = 180f,
                                    useCenter = true,
                                )
                            }
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                            )
                        }
                    }
                    if (selected) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
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
                RadioButton(
                    selected = settings.chartWindowSeconds == seconds,
                    onClick = { onChartWindowChange(seconds) },
                )
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
                value = languages.firstOrNull { it.first == settings.language }?.second ?: labelLanguageSystem,
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
                            onLanguageChange(code)
                            expanded = false
                        },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

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
            Switch(
                checked = customBackgroundEnabled,
                onCheckedChange = { onCustomBackgroundToggle(it) },
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Glassmorphism toggle — always visible, independent of custom background
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = labelCustomBackgroundBlur,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = glassmorphism,
                onCheckedChange = { onGlassmorphismToggle(it) },
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = labelCustomBackgroundBlurDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Pick image button
        OutlinedButton(
            onClick = { imagePicker.launch("image/*") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(labelCustomBackgroundPick)
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Remove background button (if image is set)
        if (customBackgroundUri != null) {
            OutlinedButton(
                onClick = { onCustomBackgroundRemove() },
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
