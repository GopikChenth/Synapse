package com.arcadelabs.synapse.features.folders.ui

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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arcadelabs.synapse.core.designsystem.FolderIcon
import com.arcadelabs.synapse.features.devices.ui.DeviceViewModel
import com.arcadelabs.synapse.features.status.ui.StatusViewModel
import org.koin.compose.viewmodel.koinViewModel

fun generateRandomFolderId(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    val part1 = (1..5).map { chars.random() }.joinToString("")
    val part2 = (1..5).map { chars.random() }.joinToString("")
    return "$part1-$part2"
}

@Composable
fun FoldersScreen(
    onAddFolderClick: () -> Unit,
    onAddDeviceClick: (String, String) -> Unit = { _, _ -> },
    openFolder: ((String) -> Unit)? = null,
    viewModel: FolderViewModel = koinViewModel(),
    deviceViewModel: DeviceViewModel = koinViewModel()
) {
    val folders by viewModel.foldersState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val pendingDevices by deviceViewModel.pendingDevices.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            isLoading && folders.isEmpty() -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            error != null && folders.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = error ?: "An error occurred",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadFolders() }) { Text("Retry") }
                }
            }
            folders.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = FolderIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No folders shared",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap + to add your first folder",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Pending connection requests banner
                    if (pendingDevices.isNotEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                                    pending.name.ifEmpty { id.take(14) + "\u2026" },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                                Text(
                                                    id.take(18) + "\u2026",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                TextButton(
                                                    onClick = { onAddDeviceClick(id, pending.name) },
                                                    colors = ButtonDefaults.textButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.primary
                                                    )
                                                ) { Text("Accept") }
                                                TextButton(
                                                    onClick = { deviceViewModel.dismissPendingDevice(id) },
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

                    // Folder cards
                    items(folders) { folder ->
                        FolderCard(
                            folder = folder,
                            onOpenClick = { openFolder?.invoke(folder.path) },
                            onDeleteFolder = { viewModel.deleteFolder(it) },
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

// ─── Add Folder Bottom Sheet ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFolderSheet(
    onDismiss: () -> Unit,
    selectDirectory: ((onPathSelected: (String) -> Unit) -> Unit)? = null,
    viewModel: FolderViewModel = koinViewModel(),
    statusViewModel: StatusViewModel = koinViewModel()
) {
    val allDevices by viewModel.devicesState.collectAsState()
    val statusState by statusViewModel.statusState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    // Filter out the local device and localhost
    val myId = statusState.myId
    val remoteDevices = remember(allDevices, myId) {
        allDevices.filter { device ->
            device.deviceID != myId &&
            !device.name.equals("localhost", ignoreCase = true) &&
            !device.name.equals("this device", ignoreCase = true)
        }
    }

    var folderLabel by remember { mutableStateOf("") }
    var folderId by remember { mutableStateOf(generateRandomFolderId()) }
    var folderPath by remember { mutableStateOf("") }
    val selectedDevices = remember { mutableStateListOf<String>() }
    var folderType by remember { mutableStateOf("sendreceive") }
    var watchForChanges by remember { mutableStateOf(true) }
    var pauseFolder by remember { mutableStateOf(false) }
    var versioningType by remember { mutableStateOf("none") }

    val canSave = folderId.isNotBlank() && folderPath.isNotBlank()
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
        ) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Add Folder",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = {
                        if (canSave) {
                            viewModel.createFolder(
                                id = folderId.trim(),
                                label = folderLabel.trim(),
                                path = folderPath.trim(),
                                type = folderType,
                                paused = pauseFolder,
                                watchForChanges = watchForChanges,
                                sharedDevices = selectedDevices.toList(),
                                versioningType = versioningType,
                                onSuccess = onDismiss
                            )
                        }
                    },
                    enabled = canSave && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Save")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            // Scrollable form
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (error != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = folderLabel,
                    onValueChange = { folderLabel = it },
                    label = { Text("Folder Label (optional)") },
                    placeholder = { Text("e.g. My Documents") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = folderId,
                    onValueChange = { folderId = it },
                    label = { Text("Folder ID") },
                    singleLine = true,
                    supportingText = { Text("Must match the ID on remote devices to sync") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = folderPath,
                    onValueChange = { folderPath = it },
                    label = { Text("Directory Path") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            selectDirectory?.invoke { selectedPath -> folderPath = selectedPath }
                        }) {
                            Icon(FolderIcon, contentDescription = "Browse", modifier = Modifier.size(20.dp))
                        }
                    },
                    placeholder = { Text("/sdcard/Documents") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Folder Type dropdown
                var showTypeDropdown by remember { mutableStateOf(false) }
                val typeLabel = when (folderType) {
                    "sendreceive" -> "Send & Receive"
                    "sendonly"    -> "Send Only"
                    "receiveonly" -> "Receive Only"
                    else          -> "Send & Receive"
                }
                ExposedDropdownMenuBox(expanded = showTypeDropdown, onExpandedChange = { showTypeDropdown = it }) {
                    OutlinedTextField(
                        value = typeLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Folder Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showTypeDropdown) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = showTypeDropdown, onDismissRequest = { showTypeDropdown = false }) {
                        listOf(
                            "sendreceive" to "Send & Receive",
                            "sendonly"    to "Send Only",
                            "receiveonly" to "Receive Only"
                        ).forEach { (value, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { folderType = value; showTypeDropdown = false })
                        }
                    }
                }

                // Share with Devices — only remote devices
                if (remoteDevices.isNotEmpty()) {
                    Text(
                        text = "Share with Devices",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    remoteDevices.forEach { device ->
                        val isChecked = selectedDevices.contains(device.deviceID)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) selectedDevices.remove(device.deviceID)
                                    else selectedDevices.add(device.deviceID)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = device.name.ifEmpty { device.deviceID.take(8) + "..." },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = isChecked,
                                onCheckedChange = {
                                    if (it) selectedDevices.add(device.deviceID)
                                    else selectedDevices.remove(device.deviceID)
                                },
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                    }
                }

                // Watch for Changes toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Watch for Changes", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Auto-detect file changes without manual rescanning",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = watchForChanges, onCheckedChange = { watchForChanges = it }, modifier = Modifier.scale(0.85f))
                }

                // Pause Folder toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Pause Folder", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Switch(checked = pauseFolder, onCheckedChange = { pauseFolder = it }, modifier = Modifier.scale(0.85f))
                }

                // File Versioning dropdown
                var showVersioningDropdown by remember { mutableStateOf(false) }
                val versioningLabel = when (versioningType) {
                    "none"      -> "No File Versioning"
                    "trashcan"  -> "Trashcan File Versioning"
                    "simple"    -> "Simple File Versioning"
                    "staggered" -> "Staggered File Versioning"
                    else        -> "No File Versioning"
                }
                ExposedDropdownMenuBox(expanded = showVersioningDropdown, onExpandedChange = { showVersioningDropdown = it }) {
                    OutlinedTextField(
                        value = versioningLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("File Versioning") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showVersioningDropdown) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = showVersioningDropdown, onDismissRequest = { showVersioningDropdown = false }) {
                        listOf(
                            "none"      to "No File Versioning",
                            "trashcan"  to "Trashcan File Versioning",
                            "simple"    to "Simple File Versioning",
                            "staggered" to "Staggered File Versioning"
                        ).forEach { (value, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { versioningType = value; showVersioningDropdown = false })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// Alias kept for backward compat
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    selectDirectory: ((onPathSelected: (String) -> Unit) -> Unit)? = null,
    viewModel: FolderViewModel = koinViewModel()
) = CreateFolderSheet(onDismiss = onDismiss, selectDirectory = selectDirectory, viewModel = viewModel)

// ─── Folder Card ──────────────────────────────────────────────────────────────

@Composable
fun FolderCard(
    folder: com.arcadelabs.synapse.core.domain.models.Folder,
    onOpenClick: () -> Unit,
    onDeleteFolder: (String) -> Unit = {},
    viewModel: FolderViewModel = koinViewModel()
) {
    var showDetail by remember { mutableStateOf(false) }

    if (showDetail) {
        FolderDetailSheet(
            folder = folder,
            onDismiss = { showDetail = false },
            onOpenInExplorer = { onOpenClick(); showDetail = false },
            onDelete = { onDeleteFolder(folder.id); showDetail = false },
            viewModel = viewModel
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDetail = true },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Folder icon in tinted circle
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = FolderIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.label.ifEmpty { folder.id },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = folder.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Status chip
            val statusColor = if (folder.paused) MaterialTheme.colorScheme.tertiary
                              else MaterialTheme.colorScheme.primary
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = statusColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = if (folder.paused) "Paused" else "Active",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ─── Folder Detail Bottom Sheet ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailSheet(
    folder: com.arcadelabs.synapse.core.domain.models.Folder,
    onDismiss: () -> Unit,
    onOpenInExplorer: () -> Unit,
    onDelete: () -> Unit,
    viewModel: FolderViewModel = koinViewModel()
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val folders by viewModel.foldersState.collectAsState()
    val liveFolder = folders.find { it.id == folder.id } ?: folder

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Folder?") },
            text = {
                Text("Remove \"${liveFolder.label.ifEmpty { liveFolder.id }}\" from Syncthing?\n\nThis will stop syncing but will NOT delete the files on disk.")
            },
            confirmButton = {
                Button(
                    onClick = { showDeleteConfirm = false; onDelete() },
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(FolderIcon, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = liveFolder.label.ifEmpty { liveFolder.id },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (liveFolder.paused) "Paused" else "Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (liveFolder.paused) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Info rows
            DetailInfoRow("Folder ID", liveFolder.id, monospace = true)
            DetailInfoRow("Path", liveFolder.path)
            DetailInfoRow("Type", when (liveFolder.type) {
                "sendreceive" -> "Send & Receive"
                "sendonly"    -> "Send Only"
                "receiveonly" -> "Receive Only"
                else          -> liveFolder.type.ifEmpty { "Send & Receive" }
            })
            DetailInfoRow("Rescan Interval", "${liveFolder.rescanIntervalS}s")
            DetailInfoRow("File Watcher", if (liveFolder.fsWatcherEnabled) "Enabled" else "Disabled")
            DetailInfoRow("Versioning",
                liveFolder.versioning.type.ifEmpty { "none" }
                    .replaceFirstChar { it.uppercase() }
            )
            DetailInfoRow("Shared With", "${liveFolder.devices.size} device${if (liveFolder.devices.size != 1) "s" else ""}")

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Action buttons row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.pauseFolder(liveFolder.id, !liveFolder.paused) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (liveFolder.paused) "Resume" else "Pause")
                }
                OutlinedButton(
                    onClick = { viewModel.rescanFolder(liveFolder.id) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Rescan")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action buttons row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenInExplorer,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Open Folder")
                }
                Button(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String, monospace: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = if (monospace)
                MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            else
                MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

// ─── Form Field helper (kept for any callers) ─────────────────────────────────

@Composable
fun FormField(
    icon: ImageVector,
    title: String,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = verticalAlignment
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = if (verticalAlignment == Alignment.Top) 2.dp else 0.dp)
                .size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) { content() }
    }
}
