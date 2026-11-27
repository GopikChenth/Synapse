package com.arcadelabs.synapse

import com.arcadelabs.synapse.core.domain.models.formatDeviceId
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.arcadelabs.synapse.ui.ConfirmDialog
import com.arcadelabs.synapse.ui.FolderOfferDialog
import com.arcadelabs.synapse.ui.PairingRequestDialog
import com.arcadelabs.synapse.core.navigation.AppModalState
import com.arcadelabs.synapse.core.domain.models.PendingFolder
import com.arcadelabs.synapse.core.domain.models.PendingFolderOffer
import com.arcadelabs.synapse.core.network.SynapseApiClient
import com.arcadelabs.synapse.core.domain.models.normalizeDeviceId
import com.arcadelabs.synapse.core.domain.models.isSynapseCore
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
import com.arcadelabs.synapse.features.settings.ui.DesktopSettingsScreen
import com.arcadelabs.synapse.features.dashboard.ui.DesktopDashboardScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.arcadelabs.synapse.core.prefs.PreferencesHelper
import qrcode.raw.QRCodeProcessor

enum class DesktopScreen(val title: String) {
    DASHBOARD("Dashboard"),
    FOLDERS("Folders"),
    DEVICES("Devices"),
    RECENT("Recent"),
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
    apiClient: SynapseApiClient = koinInject(),
    deviceViewModel: DeviceViewModel = koinViewModel()
) {
    // ── Explicit Modal State Machine (Elevates UX, DX, AX) ──────────────────
    var modalState by remember { mutableStateOf<AppModalState>(AppModalState.None) }

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
    var isQuarantineDialogOpen by remember { mutableStateOf(false) }

    // Synchronize UI flags into modalState
    LaunchedEffect(
        isCreateFolderDialogOpen,
        isEditFolderDialogOpen,
        isAddDeviceDialogOpen,
        isEditDeviceDialogOpen,
        isRestartConfirmOpen,
        isShutdownConfirmOpen
    ) {
        modalState = when {
            isCreateFolderDialogOpen -> AppModalState.CreateFolder(prefilledFolderId, prefilledFolderLabel, prefilledFolderSharedDevices)
            isEditFolderDialogOpen && folderToEdit != null -> AppModalState.EditFolder(folderToEdit!!)
            isAddDeviceDialogOpen -> AppModalState.AddDevice(prefilledDeviceId, prefilledDeviceName)
            isEditDeviceDialogOpen && deviceToEdit != null -> AppModalState.EditDevice(deviceToEdit!!)
            isRestartConfirmOpen -> AppModalState.ConfirmRestart
            isShutdownConfirmOpen -> AppModalState.ConfirmShutdown
            else -> AppModalState.None
        }
    }

    // Synchronize programmatic modalState transitions into UI flags
    LaunchedEffect(modalState) {
        when (val m = modalState) {
            is AppModalState.None -> {
                isCreateFolderDialogOpen = false
                isEditFolderDialogOpen = false
                isAddDeviceDialogOpen = false
                isEditDeviceDialogOpen = false
                isRestartConfirmOpen = false
                isShutdownConfirmOpen = false
            }
            is AppModalState.CreateFolder -> {
                prefilledFolderId = m.prefilledId
                prefilledFolderLabel = m.prefilledLabel
                prefilledFolderSharedDevices = m.prefilledSharedDevices
                isCreateFolderDialogOpen = true
            }
            is AppModalState.EditFolder -> {
                folderToEdit = m.folder
                isEditFolderDialogOpen = true
            }
            is AppModalState.AddDevice -> {
                prefilledDeviceId = m.prefilledId
                prefilledDeviceName = m.prefilledName
                isAddDeviceDialogOpen = true
            }
            is AppModalState.EditDevice -> {
                deviceToEdit = m.device
                isEditDeviceDialogOpen = true
            }
            is AppModalState.ConfirmRestart -> isRestartConfirmOpen = true
            is AppModalState.ConfirmShutdown -> isShutdownConfirmOpen = true
            else -> {}
        }
    }

    // Restart exists only on the legacy engine; hide it for Synapse Core (and while the
    // engine is unreachable). Re-checked periodically so it follows the running engine.
    var isLegacyEngine by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            isLegacyEngine = try {
                !apiClient.systemVersion().isSynapseCore()
            } catch (_: Exception) {
                false
            }
            delay(15_000)
        }
    }

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
                        SynapseLogo(
                            size = 40.dp,
                            cornerRadius = 12.dp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    },
                    modifier = Modifier.fillMaxHeight()
                ) {
                    DesktopScreen.entries.forEach { screen ->
                        val selected = currentScreen == screen
                        val icon = when (screen) {
                            DesktopScreen.DASHBOARD -> if (selected) SynapseIcons.HomeFilled else SynapseIcons.Home
                            DesktopScreen.FOLDERS -> if (selected) SynapseIcons.FolderFilled else SynapseIcons.Folder
                            DesktopScreen.DEVICES -> if (selected) SynapseIcons.DevicesFilled else SynapseIcons.Devices
                            DesktopScreen.RECENT -> SynapseIcons.History
                            DesktopScreen.SETTINGS -> if (selected) SynapseIcons.SettingsFilled else SynapseIcons.Settings
                        }
                        NavigationRailItem(
                            selected = selected,
                            onClick = { currentScreen = screen },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(screen.title) }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Restart is a no-op on Synapse Core; only the legacy engine restarts.
                    if (isLegacyEngine) {
                        NavigationRailItem(
                            selected = false,
                            onClick = { isRestartConfirmOpen = true },
                            icon = { Icon(SynapseIcons.Refresh, contentDescription = null) },
                            label = { Text("Restart") }
                        )
                    }
                    NavigationRailItem(
                        selected = false,
                        onClick = { isShutdownConfirmOpen = true },
                        icon = { Icon(SynapseIcons.Exit, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        label = { Text("Quit", color = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.padding(bottom = 16.dp)
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
                                onEditFolderClick = { folder ->
                                    folderToEdit = folder
                                    isEditFolderDialogOpen = true
                                },
                                openFolder = openFolder,
                                apiClient = apiClient,
                                onOpenQuarantineClick = { isQuarantineDialogOpen = true }
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
                                 onCloseClick = { currentScreen = DesktopScreen.DASHBOARD },
                                 embedded = true
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

            // Full-screen Add Device dialog
            FullScreenOverlay(visible = isAddDeviceDialogOpen) {
                DesktopAddDeviceDialog(
                    onDismiss = { isAddDeviceDialogOpen = false },
                    scanQrCode = scanQrCode,
                    prefilledDeviceId = prefilledDeviceId,
                    prefilledDeviceName = prefilledDeviceName
                )
            }

            if (isRestartConfirmOpen && isLegacyEngine) {
                ConfirmDialog(
                    icon = SynapseIcons.Refresh,
                    title = "Restart Synapse?",
                    text = "Syncing pauses briefly and every connected device reconnects.",
                    confirmLabel = "Restart",
                    onConfirm = {
                        isRestartConfirmOpen = false
                        coroutineScope.launch {
                            try {
                                apiClient.restart()
                            } catch (e: Exception) {
                                // expected: the connection drops while the engine restarts
                            }
                        }
                    },
                    onDismiss = { isRestartConfirmOpen = false }
                )
            }

            if (isShutdownConfirmOpen) {
                ConfirmDialog(
                    icon = SynapseIcons.Exit,
                    title = "Quit Synapse?",
                    text = "Syncing stops on this computer until you open Synapse again.",
                    confirmLabel = "Quit",
                    destructive = true,
                    onConfirm = {
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
                    onDismiss = { isShutdownConfirmOpen = false }
                )
            }

            // Incoming pairing request
            val pendingDevices by deviceViewModel.pendingDevices.collectAsState()
            if (pendingDevices.isNotEmpty() && !isAddDeviceDialogOpen) {
                val (pendingId, pendingDevice) = pendingDevices.entries.first()
                PairingRequestDialog(
                    deviceName = pendingDevice.name,
                    deviceId = pendingId,
                    address = pendingDevice.address,
                    onAccept = { deviceViewModel.acceptPendingDeviceDirect(pendingId) },
                    onConfigure = {
                        prefilledDeviceId = pendingId
                        prefilledDeviceName = pendingDevice.name
                        isAddDeviceDialogOpen = true
                        deviceViewModel.dismissPendingDevice(pendingId)
                    },
                    onDismiss = { deviceViewModel.dismissPendingDevice(pendingId) }
                )
            }

            // Incoming folder offer
            val pendingFolders by deviceViewModel.pendingFolders.collectAsState()
            if (pendingFolders.isNotEmpty() && !isCreateFolderDialogOpen) {
                val (folderId, pendingOffer) = pendingFolders.entries.first()
                val offeringDeviceId = pendingOffer.offeredBy.keys.firstOrNull() ?: ""
                val folderDetails = pendingOffer.offeredBy[offeringDeviceId] ?: PendingFolder()
                FolderOfferDialog(
                    folderLabel = folderDetails.label,
                    folderId = folderId,
                    offeredByDeviceId = offeringDeviceId,
                    onConfigure = {
                        prefilledFolderId = folderId
                        prefilledFolderLabel = folderDetails.label
                        prefilledFolderSharedDevices = listOf(offeringDeviceId)
                        isCreateFolderDialogOpen = true
                    },
                    onDismiss = {
                        deviceViewModel.dismissPendingFolder(
                            folderId = folderId,
                            deviceId = offeringDeviceId,
                            label = folderDetails.label,
                            time = folderDetails.time
                        )
                    }
                )
            }

            if (isQuarantineDialogOpen) {
                com.arcadelabs.synapse.features.quarantine.QuarantineDialog(
                    onDismissRequest = { isQuarantineDialogOpen = false }
                )
            }
        }
    }
}
