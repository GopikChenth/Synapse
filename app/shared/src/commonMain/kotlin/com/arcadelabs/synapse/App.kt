package com.arcadelabs.synapse

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import com.arcadelabs.synapse.core.domain.models.SyncthingConfig
import com.arcadelabs.synapse.core.domain.models.PendingFolder
import com.arcadelabs.synapse.core.domain.models.PendingFolderOffer
import com.arcadelabs.synapse.core.domain.models.normalizeDeviceId
import com.arcadelabs.synapse.core.network.SyncthingApiClient
import com.arcadelabs.synapse.features.devices.ui.DevicesScreen
import com.arcadelabs.synapse.features.devices.ui.AddDeviceDialog
import com.arcadelabs.synapse.features.devices.ui.DeviceViewModel
import com.arcadelabs.synapse.features.folders.ui.CreateFolderDialog
import com.arcadelabs.synapse.features.folders.ui.FoldersScreen
import com.arcadelabs.synapse.features.status.ui.StatusScreen
import com.arcadelabs.synapse.features.status.ui.RunBehavior
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import qrcode.raw.QRCodeProcessor

enum class Screen(val title: String) {
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
    apiClient: SyncthingApiClient = koinInject(),
    deviceViewModel: DeviceViewModel = koinViewModel()
) {
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

    var isCreateFolderDialogOpen by remember { mutableStateOf(false) }
    var isAddDeviceDialogOpen by remember { mutableStateOf(false) }
    var prefilledDeviceId by remember { mutableStateOf("") }
    var prefilledDeviceName by remember { mutableStateOf("") }
    var isShowDeviceIdDialogOpen by remember { mutableStateOf(false) }
    var isImportExportDialogOpen by remember { mutableStateOf(false) }
    var prefilledFolderId by remember { mutableStateOf("") }
    var prefilledFolderLabel by remember { mutableStateOf("") }
    var prefilledFolderSharedDevices by remember { mutableStateOf<List<String>>(emptyList()) }

    var localDeviceId by remember { mutableStateOf("") }
    var localDeviceName by remember { mutableStateOf("") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = Screen.FOLDERS.ordinal,
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

    MaterialTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                val drawerItemColors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = Color.Transparent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface
                )
                ModalDrawerSheet(
                    modifier = Modifier.fillMaxWidth(0.65f),
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    drawerTonalElevation = 1.dp
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = DevicesIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Synapse",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(QrCodeIcon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("Show device ID", style = MaterialTheme.typography.bodyMedium) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                drawerState.close()
                            }
                            isShowDeviceIdDialogOpen = true
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("Recent changes", style = MaterialTheme.typography.bodyMedium) },
                        selected = false,
                        onClick = {},
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(WebIcon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("Web GUI", style = MaterialTheme.typography.bodyMedium) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                drawerState.close()
                            }
                            openUrl?.invoke("http://127.0.0.1:8384")
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(ImportExportIcon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("Import and Export", style = MaterialTheme.typography.bodyMedium) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                drawerState.close()
                            }
                            isImportExportDialogOpen = true
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("Restart", style = MaterialTheme.typography.bodyMedium) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                drawerState.close()
                                try {
                                    apiClient.restart()
                                } catch(e: Exception) {}
                            }
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("Settings", style = MaterialTheme.typography.bodyMedium) },
                        selected = false,
                        onClick = {},
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(ExitIcon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("Exit", style = MaterialTheme.typography.bodyMedium) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                drawerState.close()
                            }
                            exitApp?.invoke()
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
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
                                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            Screen.entries.forEach { screen ->
                                val selected = pagerState.currentPage == screen.ordinal
                                val icon = when (screen) {
                                    Screen.FOLDERS -> FolderIcon
                                    Screen.DEVICES -> DevicesIcon
                                    Screen.STATUS -> Icons.Default.Info
                                }
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(screen.ordinal)
                                        }
                                    },
                                    icon = { Icon(icon, contentDescription = screen.title) },
                                    label = { Text(screen.title) }
                                )
                            }
                        }
                    },
                    floatingActionButton = {
                        val currentScreen = Screen.entries[pagerState.currentPage]
                        AnimatedVisibility(
                            visible = currentScreen == Screen.FOLDERS || currentScreen == Screen.DEVICES,
                            enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(),
                            exit = scaleOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
                        ) {
                            val isTransitionRunning = transition.currentState != transition.targetState
                            val targetElevation = if (isTransitionRunning) 0.dp else 6.dp
                            val fabElevation by animateDpAsState(
                                targetValue = targetElevation,
                                animationSpec = spring(stiffness = Spring.StiffnessLow)
                            )

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
                                elevation = FloatingActionButtonDefaults.elevation(
                                    defaultElevation = fabElevation,
                                    pressedElevation = fabElevation + 2.dp,
                                    focusedElevation = fabElevation,
                                    hoveredElevation = fabElevation
                                ),
                                icon = { Icon(Icons.Default.Add, contentDescription = null) },
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
                            Screen.FOLDERS -> FoldersScreen(
                                onAddFolderClick = { isCreateFolderDialogOpen = true },
                                openFolder = openFolder
                            )
                            Screen.DEVICES -> DevicesScreen(
                                onAddDeviceClick = { id, name ->
                                    prefilledDeviceId = id
                                    prefilledDeviceName = name
                                    isAddDeviceDialogOpen = true
                                }
                            )
                            Screen.STATUS -> StatusScreen(onRunBehaviorChanged = onRunBehaviorChanged)
                        }
                    }
                }

                // Root level Full-screen Add Folder Overlay
                AnimatedVisibility(
                    visible = isCreateFolderDialogOpen,
                    enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), initialScale = 0.8f) + fadeIn(),
                    exit = scaleOut(animationSpec = spring(stiffness = Spring.StiffnessMedium), targetScale = 0.8f) + fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
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
                        AddDeviceDialog(
                            onDismiss = { isAddDeviceDialogOpen = false },
                            scanQrCode = scanQrCode,
                            prefilledDeviceId = prefilledDeviceId,
                            prefilledDeviceName = prefilledDeviceName
                        )
                    }
                }

                // Show Device ID Dialog
                if (isShowDeviceIdDialogOpen) {
                    AlertDialog(
                        onDismissRequest = { isShowDeviceIdDialogOpen = false },
                        title = { Text("Local Device ID", style = MaterialTheme.typography.titleMedium) },
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
                                    QrCodeView(
                                        text = qrText,
                                        modifier = Modifier
                                            .size(200.dp)
                                            .background(Color.White)
                                            .padding(16.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                
                                Text(
                                    text = localId,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(8.dp)
                                )
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
                        title = { Text("Import / Export Configuration") },
                        text = {
                            Column {
                                Text("Exported configuration (copy below) or paste a new configuration to import:", style = MaterialTheme.typography.bodySmall)
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
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Row {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                kotlinx.serialization.json.Json.parseToJsonElement(configJsonText)
                                                apiClient.updateRawSystemConfig(configJsonText)
                                                try {
                                                    apiClient.restart()
                                                } catch (e: Exception) {
                                                    // Ignored
                                                }
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
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(onClick = { isImportExportDialogOpen = false }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    )
                }

                // Incoming Connection Request Dialog Overlay
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
                                    deviceViewModel.dismissPendingFolder(folderId)
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
}