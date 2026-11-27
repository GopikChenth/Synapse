package com.arcadelabs.synapse.features.dashboard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arcadelabs.synapse.features.devices.ui.DeviceViewModel
import com.arcadelabs.synapse.features.folders.ui.FolderViewModel
import com.arcadelabs.synapse.features.status.ui.StatusViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import com.arcadelabs.synapse.core.network.SynapseApiClient
import com.arcadelabs.synapse.core.domain.models.FolderDbStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@Composable
fun MobileDashboardScreen(
    onAddFolderClick: () -> Unit,
    onAddDeviceClick: () -> Unit,
    onShowDeviceIdClick: () -> Unit,
    onRecentChangesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavigateToFolders: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onOpenQuarantineClick: () -> Unit = {},
    folderViewModel: FolderViewModel = koinViewModel(),
    deviceViewModel: DeviceViewModel = koinViewModel(),
    statusViewModel: StatusViewModel = koinViewModel(),
    apiClient: SynapseApiClient = koinInject()
) {
    val folders by folderViewModel.foldersState.collectAsState()
    val devices by deviceViewModel.devices.collectAsState()
    val statusState by statusViewModel.statusState.collectAsState()
    
    var totalLocalBytes by remember { mutableStateOf(0L) }
    var syncActivityCount by remember { mutableStateOf(0) }
    var syncConflictsCount by remember { mutableStateOf(0L) }
    
    val clipboardManager = LocalClipboardManager.current
    var showCopyFeedback by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // M3 snackbar confirmation for "copy device ID"
    LaunchedEffect(showCopyFeedback) {
        if (showCopyFeedback) {
            snackbarHostState.showSnackbar("Device ID copied")
            showCopyFeedback = false
        }
    }

    // Refresh folders and devices immediately when Synapse starts running
    LaunchedEffect(statusState.isRunning) {
        if (statusState.isRunning) {
            folderViewModel.loadFolders()
            deviceViewModel.loadInitial()
        }
    }

    // Poll stats details periodically when the dashboard is visible
    LaunchedEffect(folders) {
        if (folders.isNotEmpty()) {
            while (true) {
                try {
                    val dbStatuses: List<FolderDbStatus?> = coroutineScope {
                        val deferreds = folders.map { folder ->
                            async {
                                try { apiClient.dbStatus(folder.id) } catch (_: Exception) { null }
                            }
                        }
                        deferreds.awaitAll()
                    }

                    var totalBytes = 0L

                    dbStatuses.filterNotNull().forEach { dbStatus ->
                        totalBytes += dbStatus.localBytes
                    }

                    totalLocalBytes = totalBytes
                    // Open conflicts from the engine (the list the Quarantine dialog shows).
                    // Pull errors are not conflicts, so they no longer feed this counter.
                    syncConflictsCount = try {
                        apiClient.getConflicts().count { !it.isTerminal }.toLong()
                    } catch (_: Exception) { syncConflictsCount }

                    try {
                        val events = apiClient.getEvents(since = 0, limit = 100)
                        syncActivityCount = events.count { it.type == "ItemFinished" }
                    } catch (_: Exception) {}

                } catch (_: Exception) {}

                // Poll every 5 seconds
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Engine status header card
            HeaderStatusCard(
                isRunning = statusState.isRunning,
                uptime = statusState.uptime
            )
            
            // 2. Summary stats cards (Folders & Devices)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val pausedCount = folders.count { it.paused }
                FolderSummaryCard(
                    foldersCount = folders.size,
                    pausedCount = pausedCount,
                    onNavigateToFolders = onNavigateToFolders,
                    modifier = Modifier.weight(1f)
                )
                
                val onlineDevices = devices.count { it.connected }
                DeviceSummaryCard(
                    devicesCount = devices.size,
                    onlineCount = onlineDevices,
                    onNavigateToDevices = onNavigateToDevices,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // 3. Detailed stats (Sync & System status details)
            Text(
                text = "Sync & System Details",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SyncedVolumeCard(
                    totalLocalBytes = totalLocalBytes,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SyncActivityCard(
                        syncActivityCount = syncActivityCount,
                        modifier = Modifier.weight(1f)
                    )
                    
                    SyncConflictsCard(
                        conflictCount = syncConflictsCount,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenQuarantineClick
                    )
                }
            }

            // 5. Shared Device ID card
            val myId = statusState.myId
            if (myId.isNotEmpty()) {
                ThisDeviceCard(
                    myId = myId,
                    onShowDeviceIdClick = onShowDeviceIdClick,
                    onCopyClick = {
                        clipboardManager.setText(AnnotatedString(myId))
                        showCopyFeedback = true
                    }
                )
            }

            if (statusState.version.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Synapse Engine ${statusState.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
        
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
