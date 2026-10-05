package com.Obscrum.pchwmonitor.ui.servers

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Obscrum.pchwmonitor.R
import com.Obscrum.pchwmonitor.data.ServerConfig
import com.Obscrum.pchwmonitor.data.network.ConnectionState
import com.Obscrum.pchwmonitor.ui.components.ConnectionBar
import com.Obscrum.pchwmonitor.ui.components.EmptyStateCta
import com.Obscrum.pchwmonitor.util.QrPayload
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    servers: List<ServerConfig>,
    activeServerId: String?,
    connectionState: ConnectionState?,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onQrPayload: (QrPayload.Result) -> Unit,
    onAddManual: () -> Unit,
    errorMessage: String? = null,
) {
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val pendingDelete = servers.find { it.id == pendingDeleteId }
    val activeServer = servers.find { it.id == activeServerId }
    val qrPrompt = stringResource(R.string.servers_qr_action)
    val defaultServerName = stringResource(R.string.default_server_name)

    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { text ->
            QrPayload.parse(text)?.let { payload -> onQrPayload(payload) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = stringResource(R.string.servers_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            },
        )
        if (servers.isNotEmpty()) {
            ConnectionBar(
                state = connectionState ?: ConnectionState.DISCONNECTED,
                serverName = activeServer?.name?.ifBlank { defaultServerName },
                labelConnecting = stringResource(R.string.connecting),
                labelConnected = stringResource(R.string.connected),
                labelDisconnected = stringResource(R.string.disconnected),
            )
        }
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    qrLauncher.launch(
                        ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt(qrPrompt)
                            setBeepEnabled(false)
                            setOrientationLocked(true)
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.servers_qr_action))
            }
            if (servers.isEmpty()) {
                EmptyStateCta(
                    title = stringResource(R.string.servers_empty_title),
                    description = stringResource(R.string.servers_empty_desc),
                    actionLabel = stringResource(R.string.servers_empty_action),
                    onAction = onAddManual,
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onAddManual,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.servers_add_manual))
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.saved_servers),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                servers.forEach { server ->
                    val active = server.id == activeServerId
                    val subtitle = server.hostname?.takeIf { it.isNotBlank() }
                        ?: "${server.ip}:${server.port}"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = stringResource(R.string.select)) {
                                onSelect(server.id)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (active) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = server.name.ifBlank { defaultServerName },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onEdit(server.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.edit),
                            )
                        }
                        IconButton(onClick = { pendingDeleteId = server.id }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.delete_label),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(text = stringResource(R.string.servers_delete_confirm_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.servers_delete_confirm_message,
                        pendingDelete.name.ifBlank { defaultServerName },
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(pendingDelete.id)
                        pendingDeleteId = null
                    },
                ) {
                    Text(text = stringResource(R.string.delete_label))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }
}
