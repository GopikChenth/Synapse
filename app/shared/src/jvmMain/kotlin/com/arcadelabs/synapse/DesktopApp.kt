package com.arcadelabs.synapse

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadelabs.synapse.core.designsystem.*
import com.arcadelabs.synapse.core.domain.models.PendingFolder
import com.arcadelabs.synapse.core.domain.models.PendingFolderOffer
import com.arcadelabs.synapse.core.network.SyncthingApiClient
import com.arcadelabs.synapse.core.domain.models.normalizeDeviceId
import com.arcadelabs.synapse.features.devices.ui.DesktopDevicesScreen
import com.arcadelabs.synapse.features.devices.ui.DesktopAddDeviceDialog
import com.arcadelabs.synapse.features.devices.ui.DesktopEditDeviceDialog
import com.arcadelabs.synapse.features.devices.ui.DeviceViewModel
import com.arcadelabs.synapse.features.folders.ui.DesktopFoldersScreen
import com.arcadelabs.synapse.features.folders.ui.DesktopCreateFolderDialog
import com.arcadelabs.synapse.features.folders.ui.DesktopEditFolderDialog
import com.arcadelabs.synapse.features.status.ui.DesktopStatusScreen
import com.arcadelabs.synapse.features.status.ui.RunBehavior
import com.arcadelabs.synapse.features.recent.ui.RecentChangesScreen
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import com.arcadelabs.synapse.features.settings.ui.DesktopSettingsScreen
import com.arcadelabs.synapse.features.dashboard.ui.DesktopDashboardScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.arcadelabs.synapse.core.prefs.PreferencesHelper
import qrcode.raw.QRCodeProcessor

enum class DesktopScreen(val title: String) {
    DASHBOARD("Dashboard"),
    FOLDERS("Folders"),
    DEVICES("Devices"),
    RECENT("Recent Changes"),
    SETTINGS("Settings")
}

@Composable
fun DesktopQrCodeView(text: String, modifier: Modifier = Modifier) {
    val grid = remember(text) {
        try {
            val qr = QRCodeProcessor(text)
            val matrix = qr.encode()
            val size = matrix.size
            Array(size) { r ->
                BooleanArray(size) { c ->
                    matrix[r][c].dark
                }
            }
        } catch (e: Exception) {
            Array(0) { BooleanArray(0) }
        }
    }

    if (grid.isNotEmpty()) {
        val size = grid.size
        Canvas(modifier = modifier) {
            val cellW = this.size.width / size
            val cellH = this.size.height / size
            for (r in 0 until size) {
                for (c in 0 until size) {
                    if (grid[r][c]) {
                        drawRect(
                            color = Color.Black,
                            topLeft = androidx.compose.ui.geometry.Offset(c * cellW, r * cellH),
                            size = androidx.compose.ui.geometry.Size(cellW + 0.5f, cellH + 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopApp(
    openFolder: ((String) -> Unit)? = null,
    selectDirectory: ((onPathSelected: (String) -> Unit) -> Unit)? = null,
    scanQrCode: ((onQrScanned: (String) -> Unit) -> Unit)? = null,
    openUrl: ((String) -> Unit)? = null,
    exitApp: (() -> Unit)? = null,
    onRunBehaviorChanged: ((RunBehavior) -> Unit)? = null,
    apiClient: SyncthingApiClient = koinInject(),
    deviceViewModel: DeviceViewModel = koinViewModel()
) {
    var isCreateFolderDialogOpen by remember { mutableStateOf(false) }
    var isEditFolderDialogOpen by remember { mutableStateOf(false) }
    var folderToEdit by remember { mutableStateOf<com.arcadelabs.synapse.core.domain.models.Folder?>(null) }
    var isAddDeviceDialogOpen by remember { mutableStateOf(false) }
    var isEditDeviceDialogOpen by remember { mutableStateOf(false) }
    var deviceToEdit by remember { mutableStateOf<com.arcadelabs.synapse.features.devices.ui.DeviceUiModel?>(null) }
    var prefilledDeviceId by remember { mutableStateOf("") }
    var prefilledDeviceName by remember { mutableStateOf("") }
    var isRestartConfirmOpen by remember { mutableStateOf(false) }
    var isShutdownConfirmOpen by remember { mutableStateOf(false) }
    var prefilledFolderId by remember { mutableStateOf("") }
    var prefilledFolderLabel by remember { mutableStateOf("") }
    var prefilledFolderSharedDevices by remember { mutableStateOf<List<String>>(emptyList()) }

    var localDeviceId by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try {
            val status = apiClient.systemStatus()
            val config = apiClient.systemConfig()
            val localDevice = config.devices.find { it.deviceID.normalizeDeviceId() == status.myID.normalizeDeviceId() }
            if (localDevice != null && (localDevice.name.isEmpty() || localDevice.name.lowercase() == "localhost")) {
                val hostName = try {
                    java.net.InetAddress.getLocalHost().hostName
                } catch (_: Exception) {
                    System.getProperty("user.name") + "-PC"
                }
                val nameToSet = hostName.ifEmpty { System.getProperty("user.name") + "-PC" }
                val updatedDevices = config.devices.map {
                    if (it.deviceID.normalizeDeviceId() == status.myID.normalizeDeviceId()) {
                        it.copy(name = nameToSet)
                    } else {
                        it
                    }
                }
                apiClient.updateSystemConfig(config.copy(devices = updatedDevices))
            }
        } catch (_: Exception) {}
    }
    val preferencesHelper = koinInject<PreferencesHelper>()
    val selectedTheme by preferencesHelper.themeFlow.collectAsState()
    val themeMode by preferencesHelper.themeModeFlow.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(DesktopScreen.DASHBOARD) }



    SynapseTheme(selectedTheme = selectedTheme, themeMode = themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    header = {
                        Box(
                            modifier = Modifier.padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = DevicesIcon,
                                contentDescription = "Synapse Logo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    DesktopScreen.entries.forEach { screen ->
                        val selected = currentScreen == screen
                        val icon = when (screen) {
                            DesktopScreen.DASHBOARD -> Icons.Default.Home
                            DesktopScreen.FOLDERS -> FolderIcon
                            DesktopScreen.DEVICES -> DevicesIcon
                            DesktopScreen.RECENT -> HistoryIcon
                            DesktopScreen.SETTINGS -> Icons.Default.Settings
                        }
                        NavigationRailItem(
                            selected = selected,
                            onClick = { currentScreen = screen },
                            icon = { Icon(icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            alwaysShowLabel = false,
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Restart Button
                    NavigationRailItem(
                        selected = false,
                        onClick = { isRestartConfirmOpen = true },
                        icon = { Icon(Icons.Default.Refresh, contentDescription = "Restart Daemon") },
                        label = { Text("Restart") },
                        alwaysShowLabel = false,
                        colors = NavigationRailItemDefaults.colors(
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    )

                    // Shutdown Button
                    NavigationRailItem(
                        selected = false,
                        onClick = { isShutdownConfirmOpen = true },
                        icon = { Icon(ExitIcon, contentDescription = "Shutdown Daemon") },
                        label = { Text("Shutdown") },
                        alwaysShowLabel = false,
                        colors = NavigationRailItemDefaults.colors(
                            unselectedIconColor = MaterialTheme.colorScheme.error,
                            unselectedTextColor = MaterialTheme.colorScheme.error
                        )
                    )
                }

                // Main content area
                Scaffold { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        when (currentScreen) {
                            DesktopScreen.DASHBOARD -> DesktopDashboardScreen(
                                onAddFolderClick = { isCreateFolderDialogOpen = true },
                                onAddDeviceClick = { id, name ->
                                    prefilledDeviceId = id
                                    prefilledDeviceName = name
                                    isAddDeviceDialogOpen = true
                                },
                                openFolder = openFolder,
                                apiClient = apiClient
                            )
                            DesktopScreen.FOLDERS -> DesktopFoldersScreen(
                                onAddFolderClick = { isCreateFolderDialogOpen = true },
                                onEditFolderClick = { folder ->
                                    folderToEdit = folder
                                    isEditFolderDialogOpen = true
                                },
                                openFolder = openFolder
                            )
                             DesktopScreen.DEVICES -> DesktopDevicesScreen(
                                 onAddDeviceClick = { id, name ->
                                     prefilledDeviceId = id
                                     prefilledDeviceName = name
                                     isAddDeviceDialogOpen = true
                                 },
                                 onEditDeviceClick = { device ->
                                     deviceToEdit = device
                                     isEditDeviceDialogOpen = true
                                 }
                             )
                             DesktopScreen.RECENT -> RecentChangesScreen(
                                 onCloseClick = { currentScreen = DesktopScreen.DASHBOARD }
                             )
                             DesktopScreen.SETTINGS -> DesktopSettingsScreen(
                                 apiClient = apiClient,
                                 openUrl = openUrl
                             )
                         }
                    }
                }
            }

            // Create Folder Dialog
            if (isCreateFolderDialogOpen) {
                DesktopCreateFolderDialog(
                    onDismiss = {
                        isCreateFolderDialogOpen = false
                        prefilledFolderId = ""
                        prefilledFolderLabel = ""
                        prefilledFolderSharedDevices = emptyList()
                    },
                    selectDirectory = selectDirectory,
                    prefilledFolderId = prefilledFolderId,
                    prefilledFolderLabel = prefilledFolderLabel,
                    prefilledSharedDevices = prefilledFolderSharedDevices
                )
            }

            // Edit Folder Dialog
            if (isEditFolderDialogOpen) {
                folderToEdit?.let { folder ->
                    DesktopEditFolderDialog(
                        folder = folder,
                        onDismiss = {
                            isEditFolderDialogOpen = false
                            folderToEdit = null
                        },
                        selectDirectory = selectDirectory
                    )
                }
            }

            // Edit Device Dialog
            if (isEditDeviceDialogOpen) {
                deviceToEdit?.let { device ->
                    DesktopEditDeviceDialog(
                        device = device,
                        onDismiss = {
                            isEditDeviceDialogOpen = false
                            deviceToEdit = null
                        }
                    )
                }
            }

            // Root level Full-screen Add Device Dialog Overlay
            AnimatedVisibility(
                visible = isAddDeviceDialogOpen,
                enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), initialScale = 0.8f) + fadeIn(),
                exit = scaleOut(animationSpec = spring(stiffness = Spring.StiffnessMedium), targetScale = 0.8f) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    DesktopAddDeviceDialog(
                        onDismiss = { isAddDeviceDialogOpen = false },
                        scanQrCode = scanQrCode,
                        prefilledDeviceId = prefilledDeviceId,
                        prefilledDeviceName = prefilledDeviceName
                    )
                }
            }

            // Restart Confirmation Dialog
            if (isRestartConfirmOpen) {
                AlertDialog(
                    onDismissRequest = { isRestartConfirmOpen = false },
                    title = { Text("Restart Syncthing Daemon?") },
                    text = { Text("Are you sure you want to restart the Syncthing daemon? This will briefly disconnect all active synchronizations.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                isRestartConfirmOpen = false
                                coroutineScope.launch {
                                    try {
                                        apiClient.restart()
                                    } catch (e: Exception) {
                                        // ignore
                                    }
                                }
                            }
                        ) {
                            Text("Restart")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { isRestartConfirmOpen = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Shutdown Confirmation Dialog
            if (isShutdownConfirmOpen) {
                AlertDialog(
                    onDismissRequest = { isShutdownConfirmOpen = false },
                    title = { Text("Shutdown Synapse?") },
                    text = { Text("This will stop the Syncthing background daemon and exit the application. Do you want to proceed?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                isShutdownConfirmOpen = false
                                coroutineScope.launch {
                                    try {
                                        apiClient.shutdown()
                                    } catch (e: Exception) {
                                        // ignore
                                    }
                                    exitApp?.invoke()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Shutdown")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { isShutdownConfirmOpen = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Incoming Connection Request Dialog Overlay
            val pendingDevices by deviceViewModel.pendingDevices.collectAsState()
            if (pendingDevices.isNotEmpty() && !isAddDeviceDialogOpen) {
                val (pendingId, pendingDevice) = pendingDevices.entries.first()
                AlertDialog(
                    onDismissRequest = { /* Keep open until action taken */ },
                    title = { Text("Incoming Connection Request") },
                    text = {
                        Column {
                            Text("A remote device wants to pair with you:")
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Name: ${pendingDevice.name.ifEmpty { "Unnamed Device" }}", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("ID: ${pendingId.chunked(4).joinToString(" ")}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            if (pendingDevice.address.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Address: ${pendingDevice.address}", fontSize = 12.sp)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                prefilledDeviceId = pendingId
                                prefilledDeviceName = pendingDevice.name
                                isAddDeviceDialogOpen = true
                            }
                        ) {
                            Text("Add Device")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                deviceViewModel.dismissPendingDevice(pendingId)
                            }
                        ) {
                            Text("Dismiss", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }

            // Incoming Folder Request Dialog Overlay
            val pendingFolders by deviceViewModel.pendingFolders.collectAsState()
            if (pendingFolders.isNotEmpty() && !isCreateFolderDialogOpen) {
                val (folderId, pendingOffer) = pendingFolders.entries.first()
                val offeringDeviceId = pendingOffer.offeredBy.keys.firstOrNull() ?: ""
                val folderDetails = pendingOffer.offeredBy[offeringDeviceId] ?: PendingFolder()
                AlertDialog(
                    onDismissRequest = { /* Keep open until action taken */ },
                    title = { Text("Folder Share Offered") },
                    text = {
                        Column {
                            Text("A remote device wants to share a folder with you:")
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Folder Label: ${folderDetails.label.ifEmpty { "Unnamed Folder" }}", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Folder ID: $folderId", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Offered By Device ID: ${offeringDeviceId.chunked(4).joinToString(" ")}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                prefilledFolderId = folderId
                                prefilledFolderLabel = folderDetails.label
                                prefilledFolderSharedDevices = listOf(offeringDeviceId)
                                isCreateFolderDialogOpen = true
                            }
                        ) {
                            Text("Add Folder")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                deviceViewModel.dismissPendingFolder(
                                    folderId = folderId,
                                    deviceId = offeringDeviceId,
                                    label = folderDetails.label,
                                    time = folderDetails.time
                                )
                            }
                        ) {
                            Text("Dismiss", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
        }
    }
}
