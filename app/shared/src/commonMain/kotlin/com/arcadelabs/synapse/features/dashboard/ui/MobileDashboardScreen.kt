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
import com.arcadelabs.synapse.core.network.SyncthingApiClient

@Composable
fun MobileDashboardScreen(
    onAddFolderClick: () -> Unit,
    onAddDeviceClick: () -> Unit,
    onShowDeviceIdClick: () -> Unit,
    onRecentChangesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavigateToFolders: () -> Unit,
    onNavigateToDevices: () -> Unit,
    folderViewModel: FolderViewModel = koinViewModel(),
    deviceViewModel: DeviceViewModel = koinViewModel(),
    statusViewModel: StatusViewModel = koinViewModel(),
    apiClient: SyncthingApiClient = koinInject()
) {
    val folders by folderViewModel.foldersState.collectAsState()
    val devices by deviceViewModel.devices.collectAsState()
    val statusState by statusViewModel.statusState.collectAsState()
    
    var totalLocalBytes by remember { mutableStateOf(0L) }
    var globalCompletionPercentage by remember { mutableStateOf(100.0) }
    var syncActivityCount by remember { mutableStateOf(0) }
    var syncConflictsCount by remember { mutableStateOf(0L) }
    
    val clipboardManager = LocalClipboardManager.current
    var showCopyFeedback by remember { mutableStateOf(false) }
    
    // Auto-clear copy feedback toast
    LaunchedEffect(showCopyFeedback) {
        if (showCopyFeedback) {
            kotlinx.coroutines.delay(2000)
            showCopyFeedback = false
        }
    }

    // Poll stats details periodically when the dashboard is visible
    LaunchedEffect(folders) {
        if (folders.isNotEmpty()) {
            while (true) {
                try {
                    var totalBytes = 0L
                    var totalInSyncBytes = 0L
                    var totalGlobalBytes = 0L
                    var totalConflicts = 0L
                    
                    folders.forEach { folder ->
                        try {
                            val dbStatus = apiClient.dbStatus(folder.id)
                            totalBytes += dbStatus.localBytes
                            totalInSyncBytes += dbStatus.inSyncBytes
                            totalGlobalBytes += dbStatus.globalBytes
                            totalConflicts += dbStatus.pullErrors
                        } catch (_: Exception) {}
                    }
                    
                    totalLocalBytes = totalBytes
                    syncConflictsCount = totalConflicts
                    globalCompletionPercentage = if (totalGlobalBytes > 0L) {
                        (totalInSyncBytes.toDouble() / totalGlobalBytes.toDouble() * 100.0).coerceIn(0.0, 100.0)
                    } else {
                        100.0
                    }
                    
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Status & Speeds Header Card
            HeaderStatusCard(
                isRunning = statusState.isRunning,
                uptime = statusState.uptime,
                downloadSpeed = statusState.downloadSpeed,
                uploadSpeed = statusState.uploadSpeed
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
                
                val activeDevices = devices.count { !it.paused }
                DeviceSummaryCard(
                    devicesCount = devices.size,
                    activeCount = activeDevices,
                    onNavigateToDevices = onNavigateToDevices,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // 3. Detailed stats (Sync & System status details)
            Text(
                text = "Sync & System Details",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SyncHealthCard(
                        globalCompletionPercentage = globalCompletionPercentage,
                        modifier = Modifier.weight(1f)
                    )
                    
                    SyncedVolumeCard(
                        totalLocalBytes = totalLocalBytes,
                        modifier = Modifier.weight(1f)
                    )
                }
                
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
                        modifier = Modifier.weight(1f)
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
                    text = "Syncthing Engine ${statusState.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
        
        AnimatedVisibility(
            visible = showCopyFeedback,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 6.dp
            ) {
                Text(
                    text = "Device ID copied to clipboard",
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
