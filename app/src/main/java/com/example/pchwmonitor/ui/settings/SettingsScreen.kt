package com.example.pchwmonitor.ui.settings

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.example.pchwmonitor.util.PATREON_URL
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pchwmonitor.data.AppSettings
import com.example.pchwmonitor.data.ThemeMode
import com.example.pchwmonitor.data.network.ConnectionState
import com.example.pchwmonitor.ui.components.ConnectionBar

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
    labelTheme: String,
    labelThemeSystem: String,
    labelThemeLight: String,
    labelThemeDark: String,
    labelLanguage: String,
    labelLanguageSystem: String,
    languages: List<Pair<String?, String>>,
    labelSave: String,
    labelSaved: String,
    labelSupport: String,
    labelSupportDescription: String,
    labelSupportPatreon: String,
    modifier: Modifier = Modifier,
    onSave: (ip: String, port: Int, theme: ThemeMode, language: String?) -> Unit,
) {
    val context = LocalContext.current
    var ip by remember { mutableStateOf(settings.serverIp) }
    var port by remember { mutableStateOf(settings.serverPort.toString()) }
    var theme by remember { mutableStateOf(settings.theme) }
    var language by remember { mutableStateOf(settings.language) }
    var saved by remember { mutableStateOf(false) }

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
                    onSave(ip.trim(), portInt, theme, language)
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
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PATREON_URL)))
            },
        ) {
            Text(labelSupportPatreon)
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
