package com.arcadelabs.synapse.features.dashboard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Canvas
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
import com.arcadelabs.synapse.core.domain.models.FolderDbStatus
import com.arcadelabs.synapse.core.domain.models.toItemFinishedData
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.jsonPrimitive
import org.koin.compose.viewmodel.koinViewModel
import com.arcadelabs.synapse.core.domain.models.normalizeDeviceId
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
                            devices = devices,
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
                            },
                            apiClient = apiClient
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
                        devices = devices,
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
                        },
                        apiClient = apiClient
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
    devices: List<DeviceUiModel>,
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

    // Folder status state tracking
    var folderStatusState by remember { mutableStateOf<FolderDbStatus?>(null) }
    var folderLatestChangeState by remember { mutableStateOf<String?>(null) }
    val json = remember { kotlinx.serialization.json.Json { ignoreUnknownKeys = true } }

    LaunchedEffect(expandedFolderId) {
        if (expandedFolderId != null) {
            while (true) {
                try {
                    val status = apiClient.dbStatus(expandedFolderId)
                    folderStatusState = status
                    
                    val events = apiClient.getEvents(limit = 100)
                    val lastEvent = events.reversed().firstOrNull { event ->
                        if (event.type == "ItemFinished" && event.data != null) {
                            val data = event.toItemFinishedData(json)
                            data != null && data.folder == expandedFolderId
                        } else false
                    }
                    if (lastEvent != null) {
                        val data = lastEvent.toItemFinishedData(json)!!
                        val actionLabel = when (data.action) {
                            "update" -> "Updated"
                            "delete" -> "Deleted"
                            else -> data.action.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        }
                        folderLatestChangeState = "$actionLabel ${data.item}"
                    } else {
                        folderLatestChangeState = "No recent changes"
                    }
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(2000)
            }
        } else {
            folderStatusState = null
            folderLatestChangeState = null
        }
    }

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
                    val rescanLabel = run {
                        val hours = folder.rescanIntervalS / 3600
                        val mins = (folder.rescanIntervalS % 3600) / 60
                        val timeStr = when {
                            folder.rescanIntervalS == 0 -> "Disabled"
                            hours > 0 -> "${hours}h"
                            else -> "${mins}m"
                        }
                        val watcherStr = if (folder.fsWatcherEnabled) "  Enabled" else "  Disabled"
                        "$timeStr$watcherStr"
                    }
                    val sharedWithLabel = if (folder.devices.isEmpty()) {
                        "Nobody"
                    } else {
                        folder.devices.map { ref ->
                            devices.find { it.id.normalizeDeviceId() == ref.deviceID.normalizeDeviceId() }?.name ?: ref.deviceID.take(7)
                        }.joinToString(", ")
                    }
                    val lastScanLabel = folderStatusState?.stateChanged?.let { isoTime ->
                        try {
                            val tIndex = isoTime.indexOf('T')
                            if (tIndex != -1) {
                                val datePart = isoTime.substring(0, tIndex)
                                val timePart = isoTime.substring(tIndex + 1, tIndex + 9)
                                "$datePart $timePart"
                            } else {
                                isoTime
                            }
                        } catch (_: Exception) {
                            isoTime
                        }
                    } ?: "Scanning..."
                    val latestChangeLabel = folderLatestChangeState ?: "No recent changes"

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
                                // Global State
                                val globalStateText = folderStatusState?.let { status ->
                                    "${status.globalFiles}  ${status.globalDirectories}  ~${formatBinaryBytes(status.globalBytes)}"
                                } ?: "Loading..."
                                FolderInfoRow(label = "Global State", value = globalStateText)

                                // Local State
                                val localStateText = folderStatusState?.let { status ->
                                    "${status.localFiles}  ${status.localDirectories}  ~${formatBinaryBytes(status.localBytes)}"
                                } ?: "Loading..."
                                FolderInfoRow(label = "Local State", value = localStateText)
                                
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
                                    value = sharedWithLabel,
                                    valueColor = if (sharedWithLabel != "Nobody") MaterialTheme.colorScheme.primary else null
                                )
                                // Last Scan
                                FolderInfoRow(label = "Last Scan", value = lastScanLabel)
                                // Latest Change
                                FolderInfoRow(label = "Latest Change", value = latestChangeLabel)

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
fun SpeedGraph(
    label: String,
    currentSpeed: Long,
    totalBytes: Long,
    color: Color,
    modifier: Modifier = Modifier
) {
    val history = remember { mutableStateListOf<Float>() }
    
    // Add current speed to history when it updates
    LaunchedEffect(currentSpeed) {
        history.add(currentSpeed.toFloat())
        if (history.size > 40) {
            history.removeAt(0)
        }
    }
    
    var targetSpeed by remember { mutableStateOf(0f) }
    var targetBytes by remember { mutableStateOf(0f) }
    LaunchedEffect(currentSpeed, totalBytes) {
        targetSpeed = currentSpeed.toFloat()
        targetBytes = totalBytes.toFloat()
    }
    val animatedSpeed by animateFloatAsState(
        targetValue = targetSpeed,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )
    val animatedBytes by animateFloatAsState(
        targetValue = targetBytes,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Text(
                    text = formatSpeed(animatedSpeed.toLong()),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = "Total: ${formatBytes(animatedBytes.toLong())}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                val width = size.width
                val height = size.height
                
                if (history.size > 1) {
                    val maxVal = history.maxOrNull()?.coerceAtLeast(1024f) ?: 1024f
                    val path = Path()
                    val fillPath = Path()
                    
                    val stepX = width / (history.size - 1)
                    
                    history.forEachIndexed { index, value ->
                        val x = index * stepX
                        val normalizedY = value / maxVal
                        val y = height - (normalizedY * (height - 4f)) - 2f
                        
                        if (index == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, height)
                            fillPath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                        
                        if (index == history.size - 1) {
                            fillPath.lineTo(x, height)
                            fillPath.close()
                        }
                    }
                    
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                color.copy(alpha = 0.25f),
                                color.copy(alpha = 0.0f)
                            )
                        )
                    )
                    
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = 2.dp.toPx())
                    )
                } else {
                    drawLine(
                        color = color.copy(alpha = 0.2f),
                        start = androidx.compose.ui.geometry.Offset(0f, height / 2),
                        end = androidx.compose.ui.geometry.Offset(width, height / 2),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }
        }
    }
}

@Composable
fun DonutChart(
    label: String,
    value: Int,
    max: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    var targetValue by remember { mutableStateOf(0f) }
    LaunchedEffect(value) {
        targetValue = value.toFloat()
    }
    
    val animatedValue by animateFloatAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )

    val ratio = if (max > 0) animatedValue / max.toFloat() else 0f
    
    val infiniteTransition = rememberInfiniteTransition()
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val isAnimating = kotlin.math.abs(animatedValue - targetValue) > 0.01f
    val targetAmplitudeFactor = if (isAnimating) 1f - ratio.coerceIn(0f, 1f) else 0f
    
    val animatedAmplitudeFactor by animateFloatAsState(
        targetValue = targetAmplitudeFactor,
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
    )

    val isComplete = value >= max
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(56.dp)
        ) {
            val outlineColor = MaterialTheme.colorScheme.outlineVariant
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 5.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2f
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                
                // Background circle
                drawCircle(
                    color = outlineColor,
                    radius = radius,
                    style = Stroke(width = strokeWidth)
                )
                
                // Foreground arc (progress)
                if (ratio > 0f) {
                    val startAngle = -90f
                    val sweepAngle = ratio * 360f
                    
                    val startRadians = startAngle * (PI.toFloat() / 180f)
                    val sweepRadians = sweepAngle * (PI.toFloat() / 180f)
                    
                    val path = Path()
                    val waveFrequency = 8f // 8 waves around the full circle
                    val waveHeightPx = 3.dp.toPx()
                    val amplitude = waveHeightPx * animatedAmplitudeFactor
                    
                    val startR = (radius + sin((startRadians * waveFrequency + phase).toDouble()) * amplitude).toFloat()
                    val startX = (centerX + cos(startRadians.toDouble()) * startR).toFloat()
                    val startY = (centerY + sin(startRadians.toDouble()) * startR).toFloat()
                    path.moveTo(startX, startY)
                    
                    val numPoints = 120
                    val delta = sweepRadians / numPoints
                    for (i in 1..numPoints) {
                        val theta = startRadians + i * delta
                        val r = (radius + sin((theta * waveFrequency + phase).toDouble()) * amplitude).toFloat()
                        val x = (centerX + cos(theta.toDouble()) * r).toFloat()
                        val y = (centerY + sin(theta.toDouble()) * r).toFloat()
                        path.lineTo(x, y)
                    }
                    
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(
                            width = strokeWidth,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                }
            }
            
            // Value text inside donut hole
            Text(
                text = "${animatedValue.toInt()}/$max",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ThisDeviceSection(
    statusState: com.arcadelabs.synapse.features.status.ui.StatusUiState,
    folders: List<com.arcadelabs.synapse.core.domain.models.Folder>,
    devices: List<DeviceUiModel>,
    copiedIdFeedback: String?,
    onCopyClick: (String) -> Unit,
    apiClient: SyncthingApiClient
) {
    // Find local device in devices list to resolve its name
    val myId = statusState.myId
    val localDevice = devices.find { it.id.normalizeDeviceId() == myId.normalizeDeviceId() }
    val localDeviceName = localDevice?.name?.ifEmpty { "This Device" } ?: "This Device"

    // Local State (Total) aggregation states
    var totalLocalFiles by remember { mutableStateOf(0L) }
    var totalLocalDirs by remember { mutableStateOf(0L) }
    var totalLocalBytes by remember { mutableStateOf(0L) }
    var syncConflictsCount by remember { mutableStateOf(0) }

    val animatedLocalFiles by animateFloatAsState(
        targetValue = totalLocalFiles.toFloat(),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )
    val animatedLocalDirs by animateFloatAsState(
        targetValue = totalLocalDirs.toFloat(),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )
    val animatedLocalBytes by animateFloatAsState(
        targetValue = totalLocalBytes.toFloat(),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )
    val animatedConflicts by animateFloatAsState(
        targetValue = syncConflictsCount.toFloat(),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )

    val animatedDownloadSpeed by animateFloatAsState(
        targetValue = statusState.downloadSpeed.toFloat(),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )
    val animatedTotalDownload by animateFloatAsState(
        targetValue = statusState.totalDownload.toFloat(),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )
    val animatedUploadSpeed by animateFloatAsState(
        targetValue = statusState.uploadSpeed.toFloat(),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )
    val animatedTotalUpload by animateFloatAsState(
        targetValue = statusState.totalUpload.toFloat(),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )
    val animatedUptime by animateFloatAsState(
        targetValue = statusState.uptime.toFloat(),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )

    LaunchedEffect(folders) {
        if (folders.isNotEmpty()) {
            var lastEventId = 0
            while (isActive) {
                try {
                    var filesSum = 0L
                    var dirsSum = 0L
                    var bytesSum = 0L
                    folders.forEach { folder ->
                        try {
                            val status = apiClient.dbStatus(folder.id)
                            filesSum += status.localFiles
                            dirsSum += status.localDirectories
                            bytesSum += status.localBytes
                        } catch (_: Exception) {}
                    }
                    totalLocalFiles = filesSum
                    totalLocalDirs = dirsSum
                    totalLocalBytes = bytesSum

                    // Only fetch events newer than the last seen id, and accumulate new conflicts
                    val events = apiClient.getEvents(since = lastEventId, limit = 100)
                    if (events.isNotEmpty()) {
                        lastEventId = events.last().id
                        syncConflictsCount += events.count { event ->
                            event.type == "ItemFinished" &&
                                event.data?.get("item")?.jsonPrimitive?.content?.contains("sync-conflict", ignoreCase = true) == true
                        }
                    }
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(3000)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "This Device",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = localDeviceName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Side-by-Side Speed Graphs
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SpeedGraph(
                    label = "Download",
                    currentSpeed = statusState.downloadSpeed,
                    totalBytes = statusState.totalDownload,
                    color = Color(0xFF10B981), // Emerald
                    modifier = Modifier.weight(1f)
                )
                SpeedGraph(
                    label = "Upload",
                    currentSpeed = statusState.uploadSpeed,
                    totalBytes = statusState.totalUpload,
                    color = Color(0xFF3B82F6), // Blue
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {                // Left Part: Text Stats
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val downloadRateText = "${formatRate(animatedDownloadSpeed.toLong() * 8)} (${formatBinaryBytes(animatedTotalDownload.toLong())})"
                        val uploadRateText = "${formatRate(animatedUploadSpeed.toLong() * 8)} (${formatBinaryBytes(animatedTotalUpload.toLong())})"
                        val localStateText = "${animatedLocalFiles.toLong()}  ${animatedLocalDirs.toLong()}  ~${formatBinaryBytes(animatedLocalBytes.toLong())}"

                        ThisDeviceStatRow("Download Rate", downloadRateText)
                        ThisDeviceStatRow("Upload Rate", uploadRateText)
                        ThisDeviceStatRow("Local State (Total)", localStateText)
                        ThisDeviceStatRow("Synced Storage Volume", "~${formatBinaryBytes(animatedLocalBytes.toLong())}")
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThisDeviceStatRow("Sync Conflicts", if (animatedConflicts.toInt() > 0) "${animatedConflicts.toInt()}" else "0")
                        ThisDeviceStatRow("Uptime", formatUptime(animatedUptime.toLong()))
                        
                        // Identification row with copy
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Identification",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clickable { onCopyClick(myId) }
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = myId.take(7),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = if (copiedIdFeedback == myId) Icons.Default.Check else CopyIcon,
                                    contentDescription = "Copy ID",
                                    tint = if (copiedIdFeedback == myId) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        
                        val osName = System.getProperty("os.name") ?: "Windows"
                        val osArch = System.getProperty("os.arch") ?: "64-bit"
                        val osLabel = if (osName.contains("Windows", ignoreCase = true)) {
                            "Windows (64-bit Intel/AMD)"
                        } else {
                            "$osName ($osArch)"
                        }
                        val verStr = statusState.version.ifEmpty { "v2.1.1" }
                        val versionText = "$verStr, $osLabel"

                        ThisDeviceStatRow("Version", versionText)
                    }
                }

                // Right Part: Donut Charts
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 24.dp)
                ) {
                    val isRunning = statusState.isRunning
                    val activeColor = Color(0xFF10B981) // Connected Emerald
                    val inactiveColor = Color(0xFFEF4444) // Disconnected Crimson
                    
                    DonutChart(
                        label = "Listeners",
                        value = if (isRunning) 3 else 0,
                        max = 3,
                        color = if (isRunning) activeColor else inactiveColor
                    )
                    DonutChart(
                        label = "Discovery",
                        value = if (isRunning) 3 else 0,
                        max = 5,
                        color = if (isRunning) activeColor else inactiveColor
                    )
                }
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
        devices.filter { 
            it.id.normalizeDeviceId() != myId.normalizeDeviceId()
        }
    }
    val allPaused = remoteDevices.isNotEmpty() && remoteDevices.all { it.paused }
    val coroutineScope = rememberCoroutineScope()
    var deviceToDelete by remember { mutableStateOf<DeviceUiModel?>(null) }

    // Device connection status and rate tracking states
    var deviceConnectionState by remember { mutableStateOf<com.arcadelabs.synapse.core.domain.models.DeviceConnection?>(null) }
    var downloadRateState by remember { mutableStateOf(0L) }
    var uploadRateState by remember { mutableStateOf(0L) }

    LaunchedEffect(expandedDeviceId) {
        if (expandedDeviceId != null) {
            var prevInBytes: Long? = null
            var prevOutBytes: Long? = null
            var lastTime = System.currentTimeMillis()
            
            while (isActive) {
                try {
                    val connResp = apiClient.systemConnections()
                    val conn = connResp.connections[expandedDeviceId]
                    if (conn != null) {
                        deviceConnectionState = conn
                        val now = System.currentTimeMillis()
                        val elapsedSec = (now - lastTime) / 1000.0
                        if (elapsedSec > 0.5) {
                            if (prevInBytes != null) {
                                val diffIn = conn.inBytesTotal - prevInBytes
                                val rateIn = (diffIn / elapsedSec).toLong()
                                downloadRateState = rateIn * 8 // bits per second
                            }
                            if (prevOutBytes != null) {
                                val diffOut = conn.outBytesTotal - prevOutBytes
                                val rateOut = (diffOut / elapsedSec).toLong()
                                uploadRateState = rateOut * 8 // bits per second
                            }
                        }
                        prevInBytes = conn.inBytesTotal
                        prevOutBytes = conn.outBytesTotal
                        lastTime = now
                    } else {
                        deviceConnectionState = null
                        downloadRateState = 0L
                        uploadRateState = 0L
                    }
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(2000)
            }
        } else {
            deviceConnectionState = null
            downloadRateState = 0L
            uploadRateState = 0L
        }
    }

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
                            folder.devices.any { it.deviceID.normalizeDeviceId() == device.id.normalizeDeviceId() }
                        }.map { it.label.ifEmpty { it.id } }
                    }
                    val downloadRateText = "${formatRate(downloadRateState)} (${formatBytes(deviceConnectionState?.inBytesTotal ?: device.inBytesTotal)})"
                    val uploadRateText = "${formatRate(uploadRateState)} (${formatBytes(deviceConnectionState?.outBytesTotal ?: device.outBytesTotal)})"
                    val addressText = deviceConnectionState?.address?.ifEmpty { null } ?: device.addressConnected.ifEmpty { null } ?: device.addresses.firstOrNull() ?: "Dynamic"
                    val connectionTypeText = deviceConnectionState?.type?.ifEmpty { null } ?: device.connectionType.ifEmpty { null } ?: "Unknown"
                    val connectionsText = if (device.connected) "1 + 1" else "0"
                    val versionText = deviceConnectionState?.clientVersion?.ifEmpty { null } ?: device.clientVersion.ifEmpty { null } ?: "v2.1.2"
                    val foldersText = sharedFolders.joinToString(", ").ifEmpty { "None" }

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
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Download Rate
                                FolderInfoRow(label = "Download Rate", value = downloadRateText)
                                // Upload Rate
                                FolderInfoRow(label = "Upload Rate", value = uploadRateText)
                                // Address
                                FolderInfoRow(label = "Address", value = addressText)
                                // Connection Type
                                FolderInfoRow(label = "Connection Type", value = connectionTypeText)
                                // Number of Connections
                                FolderInfoRow(label = "Number of Connections", value = connectionsText)
                                // Compression
                                FolderInfoRow(label = "Compression", value = "Metadata Only")
                                // Identification
                                FolderInfoRow(label = "Identification", value = device.id.take(7))
                                // Version
                                FolderInfoRow(label = "Version", value = versionText)
                                // Folders
                                FolderInfoRow(label = "Folders", value = foldersText)

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
                                    if (it.deviceID.normalizeDeviceId() != myId.normalizeDeviceId()) it.copy(paused = !allPaused) else it
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

private fun formatBinaryBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes.toDouble() / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GiB", gb)
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.2f MiB", mb)
        kb >= 1.0 -> String.format(java.util.Locale.US, "%.2f KiB", kb)
        else -> "$bytes B"
    }
}

private fun formatRate(bitsPerSec: Long): String {
    if (bitsPerSec < 1000) return "$bitsPerSec bps"
    val kbps = bitsPerSec / 1000.0
    if (kbps < 1000) return "${(kbps * 10).toLong() / 10.0} Kbps"
    val mbps = kbps / 1000.0
    return "${(mbps * 10).toLong() / 10.0} Mbps"
}

private fun formatSpeed(bytesPerSec: Long): String {
    return "${formatBytes(bytesPerSec)}/s"
}
