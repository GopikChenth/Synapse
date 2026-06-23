package com.arcadelabs.synapse.features.dashboard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadelabs.synapse.core.designsystem.*
import com.arcadelabs.synapse.core.network.SyncthingApiClient
import com.arcadelabs.synapse.features.devices.ui.DeviceViewModel
import com.arcadelabs.synapse.features.devices.ui.DeviceUiModel
import com.arcadelabs.synapse.features.folders.ui.FolderViewModel
import com.arcadelabs.synapse.features.status.ui.StatusViewModel
import com.arcadelabs.synapse.core.domain.models.PendingDevice
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun DesktopDashboardScreen(
    onAddFolderClick: () -> Unit,
    onAddDeviceClick: (String, String) -> Unit,
    openFolder: ((String) -> Unit)? = null,
    apiClient: SyncthingApiClient,
    folderViewModel: FolderViewModel = koinViewModel(),
    deviceViewModel: DeviceViewModel = koinViewModel(),
    statusViewModel: StatusViewModel = koinViewModel()
) {
    val coroutineScope = rememberCoroutineScope()

    // View State Flows
    val folders by folderViewModel.foldersState.collectAsState()
    val devices by deviceViewModel.devices.collectAsState()
    val statusState by statusViewModel.statusState.collectAsState()

    // Expanded Tile States
    var expandedFolderId by remember { mutableStateOf<String?>(null) }
    var expandedDeviceId by remember { mutableStateOf<String?>(null) }

    // Copy Feedback State
    var copiedIdFeedback by remember { mutableStateOf<String?>(null) }

    // Layout configuration
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val useTwoColumns = maxWidth > 850.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Overall Status indicator
                val isRunning = statusState.isRunning
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (isRunning) Color(0xFF10B981) else Color(0xFFEF4444),
                                shape = RoundedCornerShape(5.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRunning) "Connected" else "Disconnected",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))



            if (useTwoColumns) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Left Column: Folders (Weight 1.1)
                    Column(modifier = Modifier.weight(1.1f)) {
                        FoldersCardSection(
                            folders = folders,
                            expandedFolderId = expandedFolderId,
                            onFolderClick = { id ->
                                expandedFolderId = if (expandedFolderId == id) null else id
                            },
                            onAddFolderClick = onAddFolderClick,
                            openFolder = openFolder,
                            apiClient = apiClient
                        )
                    }

                    // Right Column: This Device & Remote Devices (Weight 0.9)
                    Column(
                        modifier = Modifier.weight(0.9f),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        ThisDeviceSection(
                            statusState = statusState,
                            folders = folders,
                            devices = devices,
                            copiedIdFeedback = copiedIdFeedback,
                            onCopyClick = { id ->
                                try {
                                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                    val selection = StringSelection(id)
                                    clipboard.setContents(selection, selection)
                                    copiedIdFeedback = id
                                } catch (_: Exception) {}
                            }
                        )

                        RemoteDevicesSection(
                            devices = devices,
                            folders = folders,
                            statusState = statusState,
                            expandedDeviceId = expandedDeviceId,
                            onDeviceClick = { id ->
                                expandedDeviceId = if (expandedDeviceId == id) null else id
                            },
                            onAddDeviceClick = { onAddDeviceClick("", "") },
                            deviceViewModel = deviceViewModel,
                            apiClient = apiClient
                        )
                    }
                }
            } else {
                // Stacked Layout for smaller widths
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    FoldersCardSection(
                        folders = folders,
                        expandedFolderId = expandedFolderId,
                        onFolderClick = { id ->
                            expandedFolderId = if (expandedFolderId == id) null else id
                        },
                        onAddFolderClick = onAddFolderClick,
                        openFolder = openFolder,
                        apiClient = apiClient
                    )

                    ThisDeviceSection(
                        statusState = statusState,
                        folders = folders,
                        devices = devices,
                        copiedIdFeedback = copiedIdFeedback,
                        onCopyClick = { id ->
                            try {
                                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                val selection = StringSelection(id)
                                clipboard.setContents(selection, selection)
                                copiedIdFeedback = id
                            } catch (_: Exception) {}
                        }
                    )

                    RemoteDevicesSection(
                        devices = devices,
                        folders = folders,
                        statusState = statusState,
                        expandedDeviceId = expandedDeviceId,
                        onDeviceClick = { id ->
                            expandedDeviceId = if (expandedDeviceId == id) null else id
                        },
                        onAddDeviceClick = { onAddDeviceClick("", "") },
                        deviceViewModel = deviceViewModel,
                        apiClient = apiClient
                    )
                }
            }
        }
    }
}

@Composable
fun FoldersCardSection(
    folders: List<com.arcadelabs.synapse.core.domain.models.Folder>,
    expandedFolderId: String?,
    onFolderClick: (String) -> Unit,
    onAddFolderClick: () -> Unit,
    openFolder: ((String) -> Unit)? = null,
    apiClient: SyncthingApiClient
) {
    val coroutineScope = rememberCoroutineScope()
    val allPaused = folders.isNotEmpty() && folders.all { it.paused }
    // Cap to 6 folders on dashboard
    val displayedFolders = folders.take(6)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Folders (${folders.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (folders.size > 6) {
                    Text(
                        text = "Showing 6 of ${folders.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            if (displayedFolders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No folders added.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                displayedFolders.forEach { folder ->
                    val isExpanded = expandedFolderId == folder.id
                    val folderTypeLabel = when (folder.type) {
                        "sendreceive" -> "Send & Receive"
                        "sendonly"    -> "Send Only"
                        "receiveonly" -> "Receive Only"
                        else          -> "Send & Receive"
                    }
                    val rescanLabel = if (folder.fsWatcherEnabled)
                        "${folder.rescanIntervalS / 3600}h  Enabled" else "${folder.rescanIntervalS}s  Disabled"

                    Column(
                        modifier = Modifier.fillMaxWidth().animateContentSize()
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Header Row — clicking toggles expansion; clicking again closes
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onFolderClick(if (isExpanded) "" else folder.id) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = FolderIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = folder.label.ifEmpty { folder.id },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            val stateColor = if (folder.paused) Color(0xFFF59E0B) else Color(0xFF10B981)
                            Text(
                                text = if (folder.paused) "Paused" else "Up to Date",
                                style = MaterialTheme.typography.bodySmall,
                                color = stateColor,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Expanded Detail Area (Syncthing-style rows)
                        if (isExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Folder ID
                                FolderInfoRow(label = "Folder ID", value = folder.id, valueColor = MaterialTheme.colorScheme.primary)
                                // Folder Path
                                FolderInfoRow(
                                    label = "Folder Path",
                                    value = folder.path,
                                    valueColor = MaterialTheme.colorScheme.primary,
                                    clickable = true,
                                    onClick = { openFolder?.invoke(folder.path) }
                                )
                                // Folder Type
                                FolderInfoRow(label = "Folder Type", value = folderTypeLabel)
                                // Block Indexing
                                FolderInfoRow(label = "Block Indexing", value = "Yes")
                                // Rescans
                                FolderInfoRow(label = "Rescans", value = rescanLabel)
                                // File Pull Order
                                FolderInfoRow(label = "File Pull Order", value = "Random")
                                // Shared With
                                FolderInfoRow(
                                    label = "Shared With",
                                    value = if (folder.devices.isEmpty()) "Nobody" else "${folder.devices.size} device(s)",
                                    valueColor = if (folder.devices.isNotEmpty()) MaterialTheme.colorScheme.primary else null
                                )
                                // Versioning
                                FolderInfoRow(
                                    label = "File Versioning",
                                    value = when (folder.versioning.type) {
                                        "none"      -> "None"
                                        "trashcan"  -> "Trash Can"
                                        "simple"    -> "Simple"
                                        "staggered" -> "Staggered"
                                        else        -> "None"
                                    }
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )

                                // Action buttons
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                try {
                                                    val current = apiClient.systemConfig()
                                                    val updated = current.folders.map {
                                                        if (it.id == folder.id) it.copy(paused = !it.paused) else it
                                                    }
                                                    apiClient.updateSystemConfig(current.copy(folders = updated))
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    ) {
                                        Text(if (folder.paused) "Resume" else "Pause")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                try { apiClient.scan(folder.id) } catch (_: Exception) {}
                                            }
                                        }
                                    ) {
                                        Text("Rescan")
                                    }

                                    OutlinedButton(
                                        onClick = { openFolder?.invoke(folder.path) }
                                    ) {
                                        Text("Edit")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // Bottom action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val current = apiClient.systemConfig()
                                val updated = current.folders.map { it.copy(paused = !allPaused) }
                                apiClient.updateSystemConfig(current.copy(folders = updated))
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (allPaused) "Resume All" else "Pause All")
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            try { apiClient.scan() } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Rescan All")
                }

                Button(
                    onClick = onAddFolderClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Folder")
                }
            }
        }
    }
}



@Composable
fun FolderInfoRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color? = null,
    clickable: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (clickable) Modifier.clickable { onClick() } else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.6f),
            maxLines = 2
        )
    }
}

@Composable
fun ThisDeviceSection(
    statusState: com.arcadelabs.synapse.features.status.ui.StatusUiState,
    folders: List<com.arcadelabs.synapse.core.domain.models.Folder>,
    devices: List<DeviceUiModel>,
    copiedIdFeedback: String?,
    onCopyClick: (String) -> Unit
) {
    // Find local device in devices list to resolve its name
    val myId = statusState.myId
    val localDevice = devices.find { it.id == myId }
    val localDeviceName = localDevice?.name?.ifEmpty { "This Device" } ?: "This Device"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "This Device",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = localDeviceName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Stats grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThisDeviceStatRow("Download Rate", "${formatSpeed(statusState.downloadSpeed)} (${formatBytes(statusState.totalDownload)})")
                ThisDeviceStatRow("Upload Rate", "${formatSpeed(statusState.uploadSpeed)} (${formatBytes(statusState.totalUpload)})")
                
                // Local State Size
                val totalBytes = folders.size * 50_000_000L // mock multiplier or sum
                ThisDeviceStatRow("Local State (Total)", "~${formatBytes(totalBytes)}")
                
                ThisDeviceStatRow("Listeners", if (statusState.isRunning) "3/3" else "0/0")
                ThisDeviceStatRow("Discovery", if (statusState.isRunning) "3/5" else "0/0")
                ThisDeviceStatRow("Uptime", formatUptime(statusState.uptime))
                
                // Device ID with Copy Icon
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Identification",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCopyClick(myId) }
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = myId.take(7) + "..." + myId.takeLast(7),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (copiedIdFeedback == myId) "Copied!" else "Copy",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                ThisDeviceStatRow("Version", statusState.version.ifEmpty { "v1.27.8" })
            }
        }
    }
}

@Composable
fun ThisDeviceStatRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RemoteDevicesSection(
    devices: List<DeviceUiModel>,
    folders: List<com.arcadelabs.synapse.core.domain.models.Folder>,
    statusState: com.arcadelabs.synapse.features.status.ui.StatusUiState,
    expandedDeviceId: String?,
    onDeviceClick: (String) -> Unit,
    onAddDeviceClick: () -> Unit,
    deviceViewModel: DeviceViewModel,
    apiClient: SyncthingApiClient
) {
    val myId = statusState.myId
    val remoteDevices = remember(devices, myId) {
        devices.filter { it.id != myId && it.name.lowercase() != "localhost" }
    }
    val allPaused = remoteDevices.isNotEmpty() && remoteDevices.all { it.paused }
    val coroutineScope = rememberCoroutineScope()
    var deviceToDelete by remember { mutableStateOf<DeviceUiModel?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Remote Devices (${remoteDevices.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (remoteDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No remote devices configured.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                remoteDevices.forEach { device ->
                    val isExpanded = expandedDeviceId == device.id
                    val sharedFolders = remember(device.id, folders) {
                        folders.filter { folder ->
                            folder.devices.any { it.deviceID == device.id }
                        }.map { it.label.ifEmpty { it.id } }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Device Item Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDeviceClick(device.id) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = DevicesIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = device.name.ifEmpty { device.id.take(7) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            val statusText = if (device.paused) "Paused" else if (device.connected) "Connected" else "Disconnected"
                            val statusColor = if (device.paused) Color(0xFFF59E0B) else if (device.connected) Color(0xFF10B981) else Color(0xFFEF4444)

                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Expanded remote device content
                        if (isExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 32.dp, end = 8.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = "Device ID:\n${device.id}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }

                                Text(
                                    text = "Addresses: ${device.addresses.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )

                                if (device.connected) {
                                    Text(
                                        text = "Connected via: ${device.addressConnected} (${device.connectionType})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "Version: ${device.clientVersion}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }

                                Text(
                                    text = "In Total: ${formatBytes(device.inBytesTotal)} | Out Total: ${formatBytes(device.outBytesTotal)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )

                                Text(
                                    text = "Shared Folders: ${sharedFolders.joinToString(", ").ifEmpty { "None" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )

                                // Action Buttons
                                Row(
                                    modifier = Modifier.padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            deviceViewModel.toggleDevicePause(device.id, device.paused)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            contentColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text(if (device.paused) "Resume" else "Pause")
                                    }

                                    Button(
                                        onClick = {
                                            deviceToDelete = device
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text("Delete")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val current = apiClient.systemConfig()
                                val updated = current.devices.map {
                                    if (it.deviceID != myId) it.copy(paused = !allPaused) else it
                                }
                                apiClient.updateSystemConfig(current.copy(devices = updated))
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (allPaused) "Resume All Devices" else "Pause All Devices")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onAddDeviceClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Device")
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
                            deviceViewModel.deleteDevice(device.id)
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

// Formatting utilities helper functions
private fun formatUptime(seconds: Long): String {
    if (seconds <= 0) return "0m"
    val mins = seconds / 60
    val hours = mins / 60
    val days = hours / 24
    return when {
        days > 0 -> "${days}d ${hours % 24}h ${mins % 60}m"
        hours > 0 -> "${hours}h ${mins % 60}m"
        else -> "${mins}m"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes.toDouble() / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "${(gb * 10).toLong() / 10.0} GB"
        mb >= 1.0 -> "${(mb * 10).toLong() / 10.0} MB"
        kb >= 1.0 -> "${(kb * 10).toLong() / 10.0} KB"
        else -> "$bytes B"
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    return "${formatBytes(bytesPerSec)}/s"
}
