package com.Obscrum.pchwmonitor.ui.servers

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.Obscrum.pchwmonitor.R
import com.Obscrum.pchwmonitor.data.FieldIssue
import com.Obscrum.pchwmonitor.data.IssueCode
import com.Obscrum.pchwmonitor.data.IssueSeverity
import com.Obscrum.pchwmonitor.data.ServerField
import com.Obscrum.pchwmonitor.util.QrPayload
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditScreen(
    viewModel: ServerEditViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    discoveredServers: List<Triple<String, String, Int>> = emptyList(),
    isScanning: Boolean = false,
    onDiscover: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val name by viewModel.name.collectAsState()
    val ip by viewModel.ip.collectAsState()
    val port by viewModel.port.collectAsState()
    val token by viewModel.token.collectAsState()
    val hostname by viewModel.hostname.collectAsState()
    val issues by viewModel.issues.collectAsState()

    val defaultServerName = stringResource(R.string.default_server_name)
    val qrPrompt = stringResource(R.string.qr_scan)

    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { text ->
            QrPayload.parse(text)?.let { payload ->
                viewModel.ip.value = payload.ip
                viewModel.port.value = payload.port.toString()
                viewModel.token.value = payload.token
                viewModel.hostname.value = payload.hostname ?: ""
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(
                        if (viewModel.isNew) R.string.server_edit_title_new else R.string.server_edit_title_edit,
                    ),
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { viewModel.name.value = it },
                label = { Text(text = stringResource(R.string.server_name)) },
                singleLine = true,
                isError = issues.any {
                    it.field == ServerField.NAME && it.severity == IssueSeverity.ERROR
                },
                modifier = Modifier.fillMaxWidth(),
            )
            FieldIssueMessages(issues = issues, field = ServerField.NAME)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = ip,
                onValueChange = { viewModel.ip.value = it },
                label = { Text(text = stringResource(R.string.ip_address)) },
                singleLine = true,
                isError = issues.any {
                    it.field == ServerField.IP && it.severity == IssueSeverity.ERROR
                },
                modifier = Modifier.fillMaxWidth(),
            )
            FieldIssueMessages(issues = issues, field = ServerField.IP)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = port,
                onValueChange = { value ->
                    viewModel.port.value = value.filter { ch -> ch.isDigit() }.take(5)
                },
                label = { Text(text = stringResource(R.string.port)) },
                singleLine = true,
                isError = issues.any {
                    it.field == ServerField.PORT && it.severity == IssueSeverity.ERROR
                },
                modifier = Modifier.fillMaxWidth(),
            )
            FieldIssueMessages(issues = issues, field = ServerField.PORT)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { viewModel.token.value = it },
                label = { Text(text = stringResource(R.string.token_optional)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = hostname,
                onValueChange = { viewModel.hostname.value = it },
                label = { Text(text = stringResource(R.string.tunnel_url_label)) },
                placeholder = { Text("my-tunnel.trycloudflare.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
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
                Text(text = stringResource(R.string.qr_scan))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDiscover,
                enabled = !isScanning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(if (isScanning) R.string.discovering else R.string.discover),
                )
            }
            if (discoveredServers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                discoveredServers.forEach { (foundName, foundIp, foundPort) ->
                    val label = "$foundName ($foundIp:$foundPort)"
                    Button(
                        onClick = {
                            viewModel.ip.value = foundIp
                            viewModel.port.value = foundPort.toString()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = label)
                    }
                }
            } else if (!isScanning) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.no_servers_found),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        viewModel.name.value = viewModel.name.value.ifBlank { defaultServerName }
                        if (viewModel.save()) onSaved()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.save))
                }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FieldIssueMessages(issues: List<FieldIssue>, field: ServerField) {
    issues.filter { it.field == field }.forEach { issue ->
        val message = when (issue.code) {
            IssueCode.EMPTY_IP -> stringResource(R.string.err_empty_ip)
            IssueCode.INVALID_PORT -> stringResource(R.string.err_invalid_port)
            IssueCode.DUPLICATE_NAME -> stringResource(R.string.err_duplicate_name)
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
