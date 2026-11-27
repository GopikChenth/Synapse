package com.arcadelabs.synapse

import com.arcadelabs.synapse.core.domain.models.formatDeviceId
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.arcadelabs.synapse.core.domain.models.EngineConfig
import com.arcadelabs.synapse.core.domain.models.PendingFolder
import com.arcadelabs.synapse.core.domain.models.PendingFolderOffer
import com.arcadelabs.synapse.core.domain.models.normalizeDeviceId
import com.arcadelabs.synapse.core.domain.models.isSynapseCore
import com.arcadelabs.synapse.core.network.SynapseApiClient
import com.arcadelabs.synapse.core.navigation.AppModalState
import com.arcadelabs.synapse.features.devices.ui.DevicesScreen
import com.arcadelabs.synapse.features.devices.ui.AddDeviceDialog
import com.arcadelabs.synapse.features.devices.ui.EditDeviceDialog
import com.arcadelabs.synapse.features.devices.ui.DeviceViewModel
import com.arcadelabs.synapse.features.folders.ui.CreateFolderDialog
import com.arcadelabs.synapse.features.folders.ui.EditFolderDialog
import com.arcadelabs.synapse.features.folders.ui.FoldersScreen
import com.arcadelabs.synapse.features.status.ui.StatusScreen
import com.arcadelabs.synapse.features.status.ui.RunBehavior
import com.arcadelabs.synapse.features.recent.ui.RecentChangesScreen
import com.arcadelabs.synapse.features.settings.ui.SettingsScreen
import com.arcadelabs.synapse.features.dashboard.ui.MobileDashboardScreen
import com.arcadelabs.synapse.core.prefs.PreferencesHelper
import com.arcadelabs.synapse.SynapseBackHandler
import com.arcadelabs.synapse.ui.ConfirmDialog
import com.arcadelabs.synapse.ui.FolderOfferDialog
import com.arcadelabs.synapse.ui.PairingRequestDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import qrcode.raw.QRCodeProcessor

enum class Screen(val title: String) {
    DASHBOARD("Dashboard"),
    FOLDERS("Folders"),
    DEVICES("Devices"),
    STATUS("Status")
}

@Composable
fun QrCodeView(text: String, modifier: Modifier = Modifier) {
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
fun App(
    openFolder: ((String) -> Unit)? = null,
    selectDirectory: ((onPathSelected: (String) -> Unit) -> Unit)? = null,
    scanQrCode: ((onQrScanned: (String) -> Unit) -> Unit)? = null,
    openUrl: ((String) -> Unit)? = null,
    exitApp: (() -> Unit)? = null,
    onRunBehaviorChanged: ((RunBehavior) -> Unit)? = null,
    showNotification: ((title: String, message: String) -> Unit)? = null,
    deviceModelName: String = "",
    // True only on platforms whose boot hook honours the "Start on boot" setting (Android).
    supportsStartOnBoot: Boolean = false,
    dynamicColorScheme: ((darkTheme: Boolean) -> ColorScheme)? = null,
    initialDeviceId: String = "",
    initialOpenAddDevice: Boolean = false,
    clearPrefilledDevice: () -> Unit = {},
    apiClient: SynapseApiClient = koinInject(),
    preferencesHelper: PreferencesHelper = koinInject(),
    deviceViewModel: DeviceViewModel = koinViewModel()
) {
    val selectedTheme by preferencesHelper.themeFlow.collectAsState()
    val themeMode by preferencesHelper.themeModeFlow.collectAsState()
    val pendingDevices by deviceViewModel.pendingDevices.collectAsState()
    val shownNotifications = remember { mutableSetOf<String>() }
    LaunchedEffect(pendingDevices) {
        if (pendingDevices.isNotEmpty()) {
            pendingDevices.forEach { (pendingId, pendingDevice) ->
                if (!shownNotifications.contains(pendingId)) {
                    shownNotifications.add(pendingId)
                    showNotification?.invoke(
                        "Incoming Pairing Request",
                        "Device: ${pendingDevice.name.ifEmpty { "Unnamed" }} wants to pair"
                    )
                }
            }
        }
    }

    LaunchedEffect(deviceModelName) {
        if (deviceModelName.isNotEmpty()) {
            try {
                val status = apiClient.systemStatus()
                val config = apiClient.systemConfig()
                val localDevice = config.devices.find { it.deviceID.normalizeDeviceId() == status.myID.normalizeDeviceId() }
                if (localDevice != null && (localDevice.name.isEmpty() || localDevice.name.lowercase() == "localhost" || localDevice.name.lowercase() == "this device")) {
                    val updatedDevices = config.devices.map {
                        if (it.deviceID.normalizeDeviceId() == status.myID.normalizeDeviceId()) {
                            it.copy(name = deviceModelName)
                        } else {
                            it
                        }
                    }
                    apiClient.updateSystemConfig(config.copy(devices = updatedDevices))
                }
            } catch (_: Exception) {}
        }
    }

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
    var isShowDeviceIdDialogOpen by remember { mutableStateOf(false) }
    var isImportExportDialogOpen by remember { mutableStateOf(false) }
    var isRestartConfirmOpen by remember { mutableStateOf(false) }
    var isRecentChangesPageOpen by remember { mutableStateOf(false) }
    var isSettingsPageOpen by remember { mutableStateOf(false) }
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
        isShowDeviceIdDialogOpen,
        isImportExportDialogOpen,
        isRestartConfirmOpen
    ) {
        modalState = when {
            isCreateFolderDialogOpen -> AppModalState.CreateFolder(prefilledFolderId, prefilledFolderLabel, prefilledFolderSharedDevices)
            isEditFolderDialogOpen && folderToEdit != null -> AppModalState.EditFolder(folderToEdit!!)
            isAddDeviceDialogOpen -> AppModalState.AddDevice(prefilledDeviceId, prefilledDeviceName)
            isEditDeviceDialogOpen && deviceToEdit != null -> AppModalState.EditDevice(deviceToEdit!!)
            isShowDeviceIdDialogOpen -> AppModalState.ShowDeviceId
            isImportExportDialogOpen -> AppModalState.ImportExportConfig
            isRestartConfirmOpen -> AppModalState.ConfirmRestart
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
                isShowDeviceIdDialogOpen = false
                isImportExportDialogOpen = false
                isRestartConfirmOpen = false
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
            is AppModalState.ShowDeviceId -> isShowDeviceIdDialogOpen = true
            is AppModalState.ImportExportConfig -> isImportExportDialogOpen = true
            is AppModalState.ConfirmRestart -> isRestartConfirmOpen = true
            else -> {}
        }
    }

    LaunchedEffect(initialDeviceId, initialOpenAddDevice) {
        if (initialOpenAddDevice) {
            prefilledDeviceId = initialDeviceId
            isAddDeviceDialogOpen = true
            clearPrefilledDevice()
        }
    }

    // Restart and the web GUI exist only on the legacy engine; hide them for Synapse Core
    // (and while the engine is unreachable). Re-checked periodically so it follows the engine.
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
    var localDeviceName by remember { mutableStateOf("") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = Screen.DASHBOARD.ordinal,
        pageCount = { Screen.entries.size }
    )

    // Load local device ID when showing the dialog
    LaunchedEffect(isShowDeviceIdDialogOpen) {
        if (isShowDeviceIdDialogOpen) {
            try {
                val status = apiClient.systemStatus()
                localDeviceId = status.myID
                val config = apiClient.systemConfig()
                localDeviceName = config.devices.find { it.deviceID.normalizeDeviceId() == status.myID.normalizeDeviceId() }?.name ?: ""
            } catch (e: Exception) {
                localDeviceId = "Error retrieving ID"
                localDeviceName = ""
            }
        }
    }

    SynapseTheme(
        selectedTheme = selectedTheme,
        themeMode = themeMode,
        dynamicColorScheme = dynamicColorScheme
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 28.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SynapseLogo(size = 36.dp, cornerRadius = 10.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Synapse",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    val closeDrawerThen: (() -> Unit) -> Unit = { action ->
                        coroutineScope.launch { drawerState.close() }
                        action()
                    }
                    val drawerItemModifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)

                    NavigationDrawerItem(
                        icon = { Icon(SynapseIcons.QrCode, contentDescription = null) },
                        label = { Text("Show device ID") },
                        selected = false,
                        onClick = { closeDrawerThen { isShowDeviceIdDialogOpen = true } },
                        modifier = drawerItemModifier
                    )
                    NavigationDrawerItem(
                        icon = { Icon(SynapseIcons.History, contentDescription = null) },
                        label = { Text("Recent changes") },
                        selected = false,
                        onClick = { closeDrawerThen { isRecentChangesPageOpen = true } },
                        modifier = drawerItemModifier
                    )
                    // Only the legacy engine serves a web GUI; Synapse Core has none.
                    if (isLegacyEngine) {
                        NavigationDrawerItem(
                            icon = { Icon(SynapseIcons.Web, contentDescription = null) },
                            label = { Text("Web GUI") },
                            selected = false,
                            onClick = { closeDrawerThen { openUrl?.invoke("http://127.0.0.1:8384") } },
                            modifier = drawerItemModifier
                        )
                    }
                    NavigationDrawerItem(
                        icon = { Icon(SynapseIcons.ImportExport, contentDescription = null) },
                        label = { Text("Import and export") },
                        selected = false,
                        onClick = { closeDrawerThen { isImportExportDialogOpen = true } },
                        modifier = drawerItemModifier
                    )
                    // Restart is a no-op on Synapse Core; only the legacy engine restarts.
                    if (isLegacyEngine) {
                        NavigationDrawerItem(
                            icon = { Icon(SynapseIcons.Refresh, contentDescription = null) },
                            label = { Text("Restart") },
                            selected = false,
                            onClick = { closeDrawerThen { isRestartConfirmOpen = true } },
                            modifier = drawerItemModifier
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))

                    NavigationDrawerItem(
                        icon = { Icon(SynapseIcons.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                        selected = false,
                        onClick = { closeDrawerThen { isSettingsPageOpen = true } },
                        modifier = drawerItemModifier
                    )
                    NavigationDrawerItem(
                        icon = { Icon(SynapseIcons.Exit, contentDescription = null) },
                        label = { Text("Exit") },
                        selected = false,
                        onClick = { closeDrawerThen { exitApp?.invoke() } },
                        modifier = drawerItemModifier
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Synapse") },
                            navigationIcon = {
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        drawerState.open()
                                    }
                                }) {
                                    Icon(SynapseIcons.Menu, contentDescription = "Menu")
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            Screen.entries.forEach { screen ->
                                val selected = pagerState.currentPage == screen.ordinal
                                val icon = when (screen) {
                                    Screen.DASHBOARD -> if (selected) SynapseIcons.HomeFilled else SynapseIcons.Home
                                    Screen.FOLDERS -> if (selected) SynapseIcons.FolderFilled else SynapseIcons.Folder
                                    Screen.DEVICES -> if (selected) SynapseIcons.DevicesFilled else SynapseIcons.Devices
                                    Screen.STATUS -> if (selected) SynapseIcons.InfoFilled else SynapseIcons.Info
                                }
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(screen.ordinal)
                                        }
                                    },
                                    icon = { Icon(icon, contentDescription = null) },
                                    label = { Text(screen.title) }
                                )
                            }
                        }
                    },
                    floatingActionButton = {
                        val currentScreen = Screen.entries[pagerState.currentPage]
                        AnimatedVisibility(
                            visible = currentScreen == Screen.FOLDERS || currentScreen == Screen.DEVICES,
                            enter = scaleIn(tween(SynapseMotion.DurationMedium2, easing = SynapseMotion.EmphasizedDecelerate)) +
                                fadeIn(tween(SynapseMotion.DurationMedium2)),
                            exit = scaleOut(tween(SynapseMotion.DurationShort4, easing = SynapseMotion.EmphasizedAccelerate)) +
                                fadeOut(tween(SynapseMotion.DurationShort4))
                        ) {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    when (currentScreen) {
                                        Screen.FOLDERS -> isCreateFolderDialogOpen = true
                                        Screen.DEVICES -> {
                                            prefilledDeviceId = ""
                                            prefilledDeviceName = ""
                                            isAddDeviceDialogOpen = true
                                        }
                                        else -> {}
                                    }
                                },
                                icon = { Icon(SynapseIcons.Add, contentDescription = null) },
                                text = {
                                    Text(
                                        when (currentScreen) {
                                            Screen.FOLDERS -> "Add folder"
                                            Screen.DEVICES -> "Add device"
                                            else -> ""
                                        }
                                    )
                                }
                            )
                        }
                    }
                ) { paddingValues ->
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) { pageIndex ->
                        val screen = Screen.entries[pageIndex]
                        when (screen) {
                            Screen.DASHBOARD -> MobileDashboardScreen(
                                onAddFolderClick = { isCreateFolderDialogOpen = true },
                                onAddDeviceClick = {
                                    prefilledDeviceId = ""
                                    prefilledDeviceName = ""
                                    isAddDeviceDialogOpen = true
                                },
                                onShowDeviceIdClick = { isShowDeviceIdDialogOpen = true },
                                onRecentChangesClick = { isRecentChangesPageOpen = true },
                                onSettingsClick = { isSettingsPageOpen = true },
                                onNavigateToFolders = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(Screen.FOLDERS.ordinal)
                                    }
                                },
                                onNavigateToDevices = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(Screen.DEVICES.ordinal)
                                    }
                                },
                                onOpenQuarantineClick = { isQuarantineDialogOpen = true }
                            )
                            Screen.FOLDERS -> FoldersScreen(
                                onAddFolderClick = { isCreateFolderDialogOpen = true },
                                onEditFolderClick = { folder ->
                                    folderToEdit = folder
                                    isEditFolderDialogOpen = true
                                },
                                openFolder = openFolder
                            )
                            Screen.DEVICES -> DevicesScreen(
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
                            Screen.STATUS -> StatusScreen(onRunBehaviorChanged = onRunBehaviorChanged)
                        }
                    }
                }

                // Root level Full-screen Add Folder Overlay
                FullScreenOverlay(visible = isCreateFolderDialogOpen) {
                    CreateFolderDialog(
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

                // Root level Full-screen Edit Folder Overlay
                FullScreenOverlay(visible = isEditFolderDialogOpen) {
                    folderToEdit?.let { folder ->
                        EditFolderDialog(
                            folder = folder,
                            onDismiss = {
                                isEditFolderDialogOpen = false
                                folderToEdit = null
                            },
                            selectDirectory = selectDirectory
                        )
                    }
                }

                // Root level Full-screen Add Device Dialog Overlay
                FullScreenOverlay(visible = isAddDeviceDialogOpen) {
                    AddDeviceDialog(
                        onDismiss = { isAddDeviceDialogOpen = false },
                        scanQrCode = scanQrCode,
                        prefilledDeviceId = prefilledDeviceId,
                        prefilledDeviceName = prefilledDeviceName
                    )
                }

                // Root level Full-screen Edit Device Dialog Overlay
                FullScreenOverlay(visible = isEditDeviceDialogOpen) {
                    deviceToEdit?.let { device ->
                        EditDeviceDialog(
                            device = device,
                            onDismiss = {
                                isEditDeviceDialogOpen = false
                                deviceToEdit = null
                            }
                        )
                    }
                }

                // Show Device ID Dialog
                if (isShowDeviceIdDialogOpen) {
                    AlertDialog(
                        onDismissRequest = { isShowDeviceIdDialogOpen = false },
                        icon = { Icon(SynapseIcons.QrCode, contentDescription = null) },
                        title = { Text("This device's ID") },
                        text = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val localId = localDeviceId.ifEmpty { "Loading..." }
                                
                                if (localId.isNotEmpty() && localId != "Loading..." && localId != "Error retrieving ID") {
                                    val qrText = if (localDeviceName.isNotEmpty()) {
                                        "syncthing://$localId?name=${localDeviceName}"
                                    } else {
                                        localId
                                    }
                                    // QR codes need a fixed dark-on-white quiet zone to scan reliably,
                                    // so this is the one place that intentionally ignores the theme.
                                    Surface(
                                        color = Color.White,
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        QrCodeView(
                                            text = qrText,
                                            modifier = Modifier
                                                .size(200.dp)
                                                .padding(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = localId,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { isShowDeviceIdDialogOpen = false }) {
                                Text("Close")
                            }
                        }
                    )
                }

                // Import / Export Dialog
                if (isImportExportDialogOpen) {
                    var configJsonText by remember { mutableStateOf("") }
                    var importError by remember { mutableStateOf<String?>(null) }
                    
                    LaunchedEffect(Unit) {
                        try {
                            val rawConfig = apiClient.rawSystemConfig()
                            val json = kotlinx.serialization.json.Json { prettyPrint = true }
                            val jsonElement = json.parseToJsonElement(rawConfig)
                            configJsonText = json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), jsonElement)
                        } catch(e: Exception) {
                            configJsonText = "Error loading config: ${e.message}"
                        }
                    }

                    AlertDialog(
                        onDismissRequest = { isImportExportDialogOpen = false },
                        icon = { Icon(SynapseIcons.ImportExport, contentDescription = null) },
                        title = { Text("Import and export") },
                        text = {
                            Column {
                                Text("Copy the configuration below to export it, or paste a configuration to import it.")
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = configJsonText,
                                    onValueChange = { configJsonText = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    label = { Text("Configuration JSON") }
                                )
                                if (importError != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = importError ?: "",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        },
                        confirmButton = {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                kotlinx.serialization.json.Json.parseToJsonElement(configJsonText)
                                                apiClient.updateRawSystemConfig(configJsonText)
                                                isImportExportDialogOpen = false
                                            } catch(e: Exception) {
                                                val msg = e.message ?: ""
                                                importError = if (
                                                    msg.contains("connect", ignoreCase = true) ||
                                                    msg.contains("127.0.0.1") ||
                                                    msg.contains("refused", ignoreCase = true) ||
                                                    msg.contains("cert", ignoreCase = true) ||
                                                    msg.contains("trust", ignoreCase = true)
                                                ) {
                                                    "Synapse is not started"
                                                } else {
                                                    "Failed to apply config: $msg"
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Text("Import")
                                }
                        },
                        dismissButton = {
                            TextButton(onClick = { isImportExportDialogOpen = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                // Incoming Connection Request Dialog Overlay
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

                // Incoming Folder Request Dialog Overlay
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

                // Restart Confirmation Dialog
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

                // Root level Full-screen Recent Changes Overlay
                FullScreenOverlay(visible = isRecentChangesPageOpen) {
                    SynapseBackHandler(enabled = isRecentChangesPageOpen, onBack = { isRecentChangesPageOpen = false })
                    RecentChangesScreen(
                        onCloseClick = { isRecentChangesPageOpen = false }
                    )
                }

                // Root level Full-screen Settings Overlay
                FullScreenOverlay(visible = isSettingsPageOpen) {
                    SynapseBackHandler(enabled = isSettingsPageOpen, onBack = { isSettingsPageOpen = false })
                    SettingsScreen(
                        onClose = { isSettingsPageOpen = false },
                        supportsStartOnBoot = supportsStartOnBoot
                    )
                }
            }
        }
    }
}