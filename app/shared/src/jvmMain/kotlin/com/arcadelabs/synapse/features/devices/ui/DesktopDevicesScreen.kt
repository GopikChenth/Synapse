package com.arcadelabs.synapse.features.devices.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import com.arcadelabs.synapse.core.designsystem.DevicesIcon
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DesktopDevicesScreen(
    onAddDeviceClick: (String, String) -> Unit,
    viewModel: DeviceViewModel = koinViewModel()
) {
    val devices by viewModel.devices.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val myId by viewModel.myId.collectAsState()

    var deviceToDelete by remember { mutableStateOf<DeviceUiModel?>(null) }

    val filteredDevices = remember(devices, myId) {
        devices.filter { it.id != myId && it.name.lowercase() != "localhost" }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddDeviceClick("", "") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Device")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                isLoading && filteredDevices.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                error != null && filteredDevices.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = error ?: "An error occurred",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.loadInitial() }) {
                            Text("Retry")
                        }
                    }
                }
                filteredDevices.isEmpty() -> {
                    Text(
                        text = "No devices connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredDevices) { device ->
                            DesktopDeviceItemCard(
                                device = device,
                                folders = folders,
                                onOpenClick = { viewModel.toggleDevicePause(device.id, device.paused) },
                                onDeleteClick = { deviceToDelete = device }
                            )
                        }
                    }
                }
            }
        }

        // Delete Confirmation Dialog
        deviceToDelete?.let { device ->
            AlertDialog(
                onDismissRequest = { deviceToDelete = null },
                title = { Text("Delete Device?") },
                text = {
                    Text("Are you sure you want to delete ${device.name.ifEmpty { "this device" }}?\n\nID: ${device.id.chunked(4).joinToString(" ")}")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteDevice(device.id)
                            deviceToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deviceToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun DesktopDeviceItemCard(
    device: DeviceUiModel,
    folders: List<com.arcadelabs.synapse.core.domain.models.Folder>,
    onOpenClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val sharedFolders = remember(device.id, folders) {
        folders.filter { folder ->
            folder.devices.any { it.deviceID == device.id }
        }.map { it.label.ifEmpty { it.id } }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenClick() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = device.name.ifEmpty { device.id.take(7) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                val statusText = if (device.paused) "Paused" else if (device.connected) "Connected" else "Disconnected"
                val statusColor = if (device.paused) Color(0xFFF59E0B) else if (device.connected) Color(0xFF10B981) else Color(0xFFEF4444)
                
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            val lastSeenStr = if (device.connected) "Connected now" else "Last seen: 10 Jun 2026, 00:18:18"
            Text(
                text = lastSeenStr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (sharedFolders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Folders:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                sharedFolders.forEach { folderLabel ->
                    Text(
                        text = "• $folderLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Device",
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Icon(
                imageVector = DevicesIcon,
                contentDescription = "Device Details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
