package com.arcadelabs.synapse.features.devices.ui

import com.arcadelabs.synapse.core.domain.models.formatDeviceId
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcadelabs.synapse.core.designsystem.SynapseIcons
import com.arcadelabs.synapse.core.designsystem.components.EmptyState
import com.arcadelabs.synapse.core.designsystem.components.ErrorState
import com.arcadelabs.synapse.core.designsystem.components.LeadingIcon
import com.arcadelabs.synapse.core.designsystem.components.StatusBadge
import com.arcadelabs.synapse.core.designsystem.components.SyncState
import com.arcadelabs.synapse.core.designsystem.components.SyncStateBadge
import com.arcadelabs.synapse.core.domain.models.normalizeDeviceId
import com.arcadelabs.synapse.core.domain.state.AsyncResource
import com.arcadelabs.synapse.ui.ConfirmDialog
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DevicesScreen(
    onAddDeviceClick: (String, String) -> Unit = { _, _ -> },
    onEditDeviceClick: (DeviceUiModel) -> Unit = {},
    viewModel: DeviceViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val devices = state.devices
    val error = state.error
    val myId = state.myId

    var deviceToDelete by remember { mutableStateOf<DeviceUiModel?>(null) }

    val filteredDevices = remember(devices, myId) {
        devices.filter { it.id.normalizeDeviceId() != myId.normalizeDeviceId() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val res = state.resource
        when {
            res is AsyncResource.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            res is AsyncResource.Error && filteredDevices.isEmpty() -> {
                ErrorState(
                    message = res.message.ifEmpty { error ?: "Couldn't load devices" },
                    onRetry = { viewModel.loadInitial() },
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            filteredDevices.isEmpty() -> {
                EmptyState(
                    icon = SynapseIcons.Devices,
                    title = "No devices yet",
                    body = "Add a device to start syncing folders with it.",
                    modifier = Modifier.align(Alignment.Center),
                    action = {
                        FilledTonalButton(onClick = { onAddDeviceClick("", "") }) { Text("Add device") }
                    }
                )
            }
            else -> {
                LazyColumn(
                    // Bottom padding keeps the last row clear of the extended FAB.
                    contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredDevices, key = { it.id }) { device ->
                        DeviceItemCard(
                            device = device,
                            onOpenClick = { onEditDeviceClick(device) },
                            onDeleteClick = { deviceToDelete = device },
                            onPauseClick = { viewModel.toggleDevicePause(device.id, device.paused) }
                        )
                    }
                }
            }
        }

        deviceToDelete?.let { device ->
            ConfirmDialog(
                icon = SynapseIcons.Delete,
                title = "Remove ${device.name.ifEmpty { "this device" }}?",
                text = "Folders stop syncing with this device. You can add it again later.\n\n${device.id.formatDeviceId()}",
                confirmLabel = "Remove",
                destructive = true,
                onConfirm = {
                    viewModel.deleteDevice(device.id)
                    deviceToDelete = null
                },
                onDismiss = { deviceToDelete = null }
            )
        }
    }
}

/** Device row: M3 two/three-line list item with leading icon, status and overflow menu. */
@Composable
fun DeviceItemCard(
    device: DeviceUiModel,
    onOpenClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onPauseClick: () -> Unit
) {
    val syncState = when {
        device.paused -> SyncState.Paused
        device.connected -> SyncState.Connected
        else -> SyncState.Disconnected
    }

    ListItem(
        modifier = Modifier.clickable(onClick = onOpenClick),
        leadingContent = { LeadingIcon(SynapseIcons.Devices) },
        headlineContent = {
            Text(
                text = device.name.ifEmpty { device.id.take(7) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SyncStateBadge(syncState)
                    if (device.connected) {
                        val isBep = device.clientVersion.contains("bep", ignoreCase = true) ||
                            device.clientVersion.contains("syncthing", ignoreCase = true) ||
                            device.connectionType.contains("bep", ignoreCase = true)
                        StatusBadge(
                            text = if (isBep) "BEP · TLS 1.3" else "QUIC",
                            containerColor = if (isBep) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = if (isBep) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                if (device.sharedFolders.isNotEmpty()) {
                    Text(
                        text = device.sharedFolders.joinToString(", "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        trailingContent = {
            DeviceOverflowMenu(
                paused = device.paused,
                onPauseClick = onPauseClick,
                onDeleteClick = onDeleteClick
            )
        }
    )
}

@Composable
internal fun DeviceOverflowMenu(
    paused: Boolean,
    onPauseClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(SynapseIcons.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(if (paused) "Resume" else "Pause") },
                leadingIcon = {
                    Icon(if (paused) SynapseIcons.PlayArrow else SynapseIcons.Pause, contentDescription = null)
                },
                onClick = {
                    showMenu = false
                    onPauseClick()
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Remove") },
                leadingIcon = { Icon(SynapseIcons.Delete, contentDescription = null) },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error
                ),
                onClick = {
                    showMenu = false
                    onDeleteClick()
                }
            )
        }
    }
}

/** Inline pairing request card (M3 filled card on the secondary container). */
@Composable
fun PendingDeviceCard(
    deviceId: String,
    pendingDevice: com.arcadelabs.synapse.core.domain.models.PendingDevice,
    onAddClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Pairing request", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(pendingDevice.name.ifEmpty { "Unnamed device" }, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(deviceId.formatDeviceId(), style = MaterialTheme.typography.bodySmall)
            if (pendingDevice.address.isNotEmpty()) {
                Text(pendingDevice.address, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismissClick) { Text("Dismiss") }
                Button(onClick = onAddClick) { Text("Add device") }
            }
        }
    }
}
