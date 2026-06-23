package com.arcadelabs.synapse.features.devices.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadelabs.synapse.core.designsystem.DevicesIcon
import com.arcadelabs.synapse.core.designsystem.FolderIcon
import com.arcadelabs.synapse.core.designsystem.QrCodeIcon
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopAddDeviceDialog(
    onDismiss: () -> Unit,
    scanQrCode: ((onQrScanned: (String) -> Unit) -> Unit)? = null,
    prefilledDeviceId: String = "",
    prefilledDeviceName: String = "",
    viewModel: DeviceViewModel = koinViewModel()
) {
    val folders by viewModel.folders.collectAsState()
    val error by viewModel.error.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val pendingDevices by viewModel.pendingDevices.collectAsState()

    // Form State
    var deviceId by remember { mutableStateOf(prefilledDeviceId) }
    var deviceName by remember { mutableStateOf(prefilledDeviceName) }
    var deviceAddresses by remember { mutableStateOf("dynamic") }
    val selectedFolders = remember { mutableStateListOf<String>() }
    var isIntroducer by remember { mutableStateOf(false) }
    var isAutoAccept by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var isUntrusted by remember { mutableStateOf(false) }

    val canSave = deviceId.isNotBlank()

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(DevicesIcon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Add Device", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Error banner
                if (error != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Pending devices banner
                if (pendingDevices.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Pending Requests",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                            pendingDevices.forEach { (id, pending) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            pending.name.ifEmpty { id.take(12) + "…" },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            id.take(20) + "…",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                    Row {
                                        // Accept — prefills form
                                        IconButton(onClick = {
                                            deviceId = id
                                            deviceName = pending.name
                                        }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Check, contentDescription = "Accept",
                                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }
                                        // Dismiss
                                        IconButton(onClick = { viewModel.dismissPendingDevice(id) },
                                            modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Dismiss",
                                                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Device ID
                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { deviceId = it },
                    label = { Text("Device ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    supportingText = { Text("The unique ID of the remote device") },
                    trailingIcon = {
                        if (scanQrCode != null) {
                            IconButton(onClick = { scanQrCode { scanned -> deviceId = scanned } }) {
                                Icon(QrCodeIcon, contentDescription = "Scan QR", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                )

                // Name
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("Device Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Addresses
                OutlinedTextField(
                    value = deviceAddresses,
                    onValueChange = { deviceAddresses = it },
                    label = { Text("Addresses") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Use 'dynamic' or enter IP:port") }
                )

                // Share folders
                if (folders.isNotEmpty()) {
                    Text(
                        "Share Folders",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    folders.forEach { folder ->
                        val isChecked = selectedFolders.contains(folder.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) selectedFolders.remove(folder.id)
                                    else selectedFolders.add(folder.id)
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                folder.label.ifEmpty { folder.id },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = isChecked,
                                onCheckedChange = { if (it) selectedFolders.add(folder.id) else selectedFolders.remove(folder.id) },
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Toggles
                ToggleRow("Introducer", isIntroducer) { isIntroducer = it }
                ToggleRow("Auto Accept Folders", isAutoAccept) { isAutoAccept = it }
                ToggleRow("Pause Device", isPaused) { isPaused = it }
                ToggleRow("Untrusted Device", isUntrusted) { isUntrusted = it }

                if (isUntrusted) {
                    Text(
                        "All folders shared with this device must be protected by a password.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canSave) {
                        val addressList = deviceAddresses.split(",")
                            .map { it.trim() }.filter { it.isNotEmpty() }
                        viewModel.createDevice(
                            id = deviceId.trim(),
                            name = deviceName.trim(),
                            addresses = addressList,
                            introducer = isIntroducer,
                            autoAccept = isAutoAccept,
                            paused = isPaused,
                            untrusted = isUntrusted,
                            sharedFolders = selectedFolders.toList(),
                            onSuccess = onDismiss
                        )
                    }
                },
                enabled = canSave && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Add Device")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onToggle, modifier = Modifier.scale(0.8f))
    }
}
