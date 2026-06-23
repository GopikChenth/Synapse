package com.arcadelabs.synapse.features.devices.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arcadelabs.synapse.core.designsystem.DevicesIcon
import org.koin.compose.viewmodel.koinViewModel

// ─── Devices Screen ───────────────────────────────────────────────────────────

@Composable
fun DevicesScreen(
    onAddDeviceClick: (String, String) -> Unit = { _, _ -> },
    viewModel: DeviceViewModel = koinViewModel()
) {
    val devices by viewModel.devices.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val myId by viewModel.myId.collectAsState()
    val pendingDevices by viewModel.pendingDevices.collectAsState()

    val filteredDevices = remember(devices, myId) {
        devices.filter { it.id != myId && it.name.lowercase() != "localhost" }
    }

    var deviceToDetail by remember { mutableStateOf<DeviceUiModel?>(null) }

    // Detail sheet
    deviceToDetail?.let { device ->
        DeviceDetailSheet(
            device = device,
            folders = folders,
            onDismiss = { deviceToDetail = null },
            viewModel = viewModel
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            isLoading && filteredDevices.isEmpty() && pendingDevices.isEmpty() -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            error != null && filteredDevices.isEmpty() && pendingDevices.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = error ?: "An error occurred",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { viewModel.loadInitial() }) { Text("Retry") }
                }
            }
            filteredDevices.isEmpty() && pendingDevices.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        DevicesIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No devices connected",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap + to add a remote device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 88.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Pending connection requests banner
                    if (pendingDevices.isNotEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        "Pending Connection Requests",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    pendingDevices.forEach { (id, pending) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    pending.name.ifEmpty { id.take(14) + "…" },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                                Text(
                                                    id.chunked(7).take(2).joinToString("-") + "…",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                                )
                                                if (pending.address.isNotEmpty()) {
                                                    Text(
                                                        pending.address,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                TextButton(
                                                    onClick = { onAddDeviceClick(id, pending.name) },
                                                    colors = ButtonDefaults.textButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.primary
                                                    )
                                                ) { Text("Accept") }
                                                TextButton(
                                                    onClick = { viewModel.dismissPendingDevice(id) },
                                                    colors = ButtonDefaults.textButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.error
                                                    )
                                                ) { Text("Dismiss") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Device cards
                    items(filteredDevices) { device ->
                        DeviceItemCard(
                            device = device,
                            onTap = { deviceToDetail = device }
                        )
                    }
                }
            }
        }
    }
}

// ─── Device Card ─────────────────────────────────────────────────────────────

@Composable
fun DeviceItemCard(
    device: DeviceUiModel,
    onTap: () -> Unit
) {
    val statusText = when {
        device.paused    -> "Paused"
        device.connected -> "Connected"
        else             -> "Disconnected"
    }
    val statusColor = when {
        device.paused    -> Color(0xFFF59E0B)
        device.connected -> Color(0xFF10B981)
        else             -> Color(0xFFEF4444)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .padding(vertical = 14.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    DevicesIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name.ifEmpty { device.id.take(7) + "…" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (device.connected) "Connected now" else "Disconnected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = statusColor.copy(alpha = 0.12f)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

// ─── Device Detail Sheet ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailSheet(
    device: DeviceUiModel,
    folders: List<com.arcadelabs.synapse.core.domain.models.Folder>,
    onDismiss: () -> Unit,
    viewModel: DeviceViewModel = koinViewModel()
) {
    val devices by viewModel.devices.collectAsState()
    val liveDevice = devices.find { it.id == device.id } ?: device

    val sharedFolders = remember(liveDevice.id, folders) {
        folders.filter { folder -> folder.devices.any { it.deviceID == liveDevice.id } }
               .map { it.label.ifEmpty { it.id } }
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Device?") },
            text = {
                Text(
                    "Remove \"${liveDevice.name.ifEmpty { "this device" }}\" from Syncthing?\n\n" +
                    "ID: ${liveDevice.id.chunked(4).joinToString(" ")}"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDevice(liveDevice.id)
                        showDeleteConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
        return
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            DevicesIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        liveDevice.name.ifEmpty { liveDevice.id.take(10) + "…" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    val statusColor = when {
                        liveDevice.paused    -> Color(0xFFF59E0B)
                        liveDevice.connected -> Color(0xFF10B981)
                        else                 -> Color(0xFFEF4444)
                    }
                    val statusText = when {
                        liveDevice.paused    -> "Paused"
                        liveDevice.connected -> "Connected"
                        else                 -> "Disconnected"
                    }
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            DeviceInfoRow("Device ID", liveDevice.id, monospace = true)
            if (liveDevice.addressConnected.isNotEmpty()) {
                DeviceInfoRow("Connected At", liveDevice.addressConnected)
            }
            DeviceInfoRow("Addresses", liveDevice.addresses.joinToString(", ").ifEmpty { "dynamic" })
            if (liveDevice.clientVersion.isNotEmpty()) {
                DeviceInfoRow("Client Version", liveDevice.clientVersion)
            }
            if (liveDevice.connectionType.isNotEmpty()) {
                DeviceInfoRow("Connection Type", liveDevice.connectionType)
            }
            if (liveDevice.inBytesTotal > 0 || liveDevice.outBytesTotal > 0) {
                DeviceInfoRow(
                    "Total In / Out",
                    "${formatBytes(liveDevice.inBytesTotal)} / ${formatBytes(liveDevice.outBytesTotal)}"
                )
            }
            DeviceInfoRow(
                "Shared Folders",
                if (sharedFolders.isEmpty()) "None" else sharedFolders.joinToString(", ")
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { viewModel.toggleDevicePause(liveDevice.id, liveDevice.paused) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (liveDevice.paused) "Resume" else "Pause")
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Delete Device")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DeviceInfoRow(label: String, value: String, monospace: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            value,
            style = if (monospace)
                MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            else
                MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.65f),
            maxLines = 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

// ─── PendingDeviceCard (kept for backward compat) ────────────────────────────

@Composable
fun PendingDeviceCard(
    deviceId: String,
    pendingDevice: com.arcadelabs.synapse.core.domain.models.PendingDevice,
    onAddClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "Incoming Connection Request",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                pendingDevice.name.ifEmpty { "Unnamed Device" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "ID: ${deviceId.chunked(4).joinToString(" ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (pendingDevice.address.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Address: ${pendingDevice.address}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismissClick) {
                    Text("Dismiss", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onAddClick) { Text("Add Device") }
            }
        }
    }
}
