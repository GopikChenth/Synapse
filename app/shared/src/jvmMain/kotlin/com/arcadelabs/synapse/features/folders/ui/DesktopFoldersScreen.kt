package com.arcadelabs.synapse.features.folders.ui

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arcadelabs.synapse.core.designsystem.FolderIcon
import com.arcadelabs.synapse.features.status.ui.StatusViewModel
import org.koin.compose.viewmodel.koinViewModel
import com.arcadelabs.synapse.core.domain.models.normalizeDeviceId

private fun generateRandomFolderId(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    val part1 = (1..5).map { chars.random() }.joinToString("")
    val part2 = (1..5).map { chars.random() }.joinToString("")
    return "$part1-$part2"
}

@Composable
fun DesktopFoldersScreen(
    onAddFolderClick: () -> Unit,
    openFolder: ((String) -> Unit)? = null,
    viewModel: FolderViewModel = koinViewModel()
) {
    val folders by viewModel.foldersState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddFolderClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Folder")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading && folders.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                error != null && folders.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = error ?: "An error occurred",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadFolders() }) {
                            Text("Retry")
                        }
                    }
                }
                folders.isEmpty() -> {
                    Text(
                        text = "No folders shared. Tap + to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(folders) { folder ->
                            DesktopFolderCard(
                                folder = folder,
                                onOpenClick = { openFolder?.invoke(folder.path) },
                                onDeleteFolder = { viewModel.deleteFolder(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopCreateFolderDialog(
    onDismiss: () -> Unit,
    selectDirectory: ((onPathSelected: (String) -> Unit) -> Unit)? = null,
    prefilledFolderId: String = "",
    prefilledFolderLabel: String = "",
    prefilledSharedDevices: List<String> = emptyList(),
    viewModel: FolderViewModel = koinViewModel(),
    statusViewModel: StatusViewModel = koinViewModel()
) {
    val allDevices by viewModel.devicesState.collectAsState()
    val statusState by statusViewModel.statusState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    // Only show remote devices — exclude this device itself
    val myId = statusState.myId
    val remoteDevices = allDevices.filter { device ->
        device.deviceID.normalizeDeviceId() != myId.normalizeDeviceId()
    }

    // Form States
    var folderLabel by remember { mutableStateOf(prefilledFolderLabel) }
    var folderId by remember { mutableStateOf(prefilledFolderId.ifEmpty { generateRandomFolderId() }) }
    var folderPath by remember { mutableStateOf("") }
    val selectedDevices = remember { mutableStateListOf<String>().apply { addAll(prefilledSharedDevices.map { it.normalizeDeviceId() }) } }
    var folderType by remember { mutableStateOf("sendreceive") }
    var watchForChanges by remember { mutableStateOf(true) }
    var pauseFolder by remember { mutableStateOf(false) }
    var versioningType by remember { mutableStateOf("none") }

    val canSave = folderId.isNotBlank() && folderPath.isNotBlank()

    // Auto-focus the label field when the dialog opens
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Add Folder",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Error banner
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

                // Folder Label — auto-focused on open
                OutlinedTextField(
                    value = folderLabel,
                    onValueChange = { folderLabel = it },
                    label = { Text("Folder Label (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    placeholder = { Text("e.g. My Documents") }
                )

                // Folder ID
                OutlinedTextField(
                    value = folderId,
                    onValueChange = { folderId = it },
                    label = { Text("Folder ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Must match the ID on remote devices to sync") }
                )

                // Directory Path
                OutlinedTextField(
                    value = folderPath,
                    onValueChange = { folderPath = it },
                    label = { Text("Directory Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            selectDirectory?.invoke { selectedPath ->
                                folderPath = selectedPath
                            }
                        }) {
                            Icon(
                                FolderIcon,
                                contentDescription = "Browse",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    placeholder = { Text("e.g. C:\\Users\\you\\Documents") }
                )

                // Folder Type dropdown
                var showTypeDropdown by remember { mutableStateOf(false) }
                val typeLabel = when (folderType) {
                    "sendreceive" -> "Send & Receive"
                    "sendonly"    -> "Send Only"
                    "receiveonly" -> "Receive Only"
                    else          -> "Send & Receive"
                }
                ExposedDropdownMenuBox(
                    expanded = showTypeDropdown,
                    onExpandedChange = { showTypeDropdown = it }
                ) {
                    OutlinedTextField(
                        value = typeLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Folder Type") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showTypeDropdown)
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = showTypeDropdown,
                        onDismissRequest = { showTypeDropdown = false }
                    ) {
                        listOf(
                            "sendreceive" to "Send & Receive",
                            "sendonly"    to "Send Only",
                            "receiveonly" to "Receive Only"
                        ).forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { folderType = value; showTypeDropdown = false }
                            )
                        }
                    }
                }

                // Share with Devices — only remote devices, not self
                if (remoteDevices.isNotEmpty()) {
                    Text(
                        text = "Share with Devices",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    remoteDevices.forEach { device ->
                        val isChecked = selectedDevices.contains(device.deviceID.normalizeDeviceId())
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) selectedDevices.remove(device.deviceID.normalizeDeviceId())
                                    else selectedDevices.add(device.deviceID.normalizeDeviceId())
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
                                onCheckedChange = { checked ->
                                    if (checked) selectedDevices.add(device.deviceID.normalizeDeviceId())
                                    else selectedDevices.remove(device.deviceID.normalizeDeviceId())
                                },
                                modifier = Modifier.scale(0.8f)
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
                        Text(
                            "Watch for Changes",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Auto-detect file changes without manual rescanning",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = watchForChanges,
                        onCheckedChange = { watchForChanges = it },
                        modifier = Modifier.scale(0.8f)
                    )
                }

                // Pause Folder toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Pause Folder",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = pauseFolder,
                        onCheckedChange = { pauseFolder = it },
                        modifier = Modifier.scale(0.8f)
                    )
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
                ExposedDropdownMenuBox(
                    expanded = showVersioningDropdown,
                    onExpandedChange = { showVersioningDropdown = it }
                ) {
                    OutlinedTextField(
                        value = versioningLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("File Versioning") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showVersioningDropdown)
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = showVersioningDropdown,
                        onDismissRequest = { showVersioningDropdown = false }
                    ) {
                        listOf(
                            "none"      to "No File Versioning",
                            "trashcan"  to "Trashcan File Versioning",
                            "simple"    to "Simple File Versioning",
                            "staggered" to "Staggered File Versioning"
                        ).forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { versioningType = value; showVersioningDropdown = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
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
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Add Folder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DesktopFormField(
    icon: ImageVector,
    title: String,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
        Column(
            modifier = Modifier.weight(1f)
        ) {
            content()
        }
    }
}

@Composable
fun DesktopFolderCard(
    folder: com.arcadelabs.synapse.core.domain.models.Folder,
    onOpenClick: () -> Unit,
    onDeleteFolder: (String) -> Unit = {},
    viewModel: FolderViewModel = koinViewModel()
) {
    var showDetail by remember { mutableStateOf(false) }

    if (showDetail) {
        FolderDetailDialog(
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = FolderIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
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
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (folder.paused) "Paused" else "Active",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (folder.paused)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun FolderDetailDialog(
    folder: com.arcadelabs.synapse.core.domain.models.Folder,
    onDismiss: () -> Unit,
    onOpenInExplorer: () -> Unit,
    onDelete: () -> Unit,
    viewModel: FolderViewModel = koinViewModel()
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Track live folder state so Pause/Resume reflects immediately
    val folders by viewModel.foldersState.collectAsState()
    val livFolder = folders.find { it.id == folder.id } ?: folder

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Folder?") },
            text = {
                Text("Remove \"${livFolder.label.ifEmpty { livFolder.id }}\" from Syncthing?\n\nThis will stop syncing but will NOT delete the files on disk.")
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = FolderIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = livFolder.label.ifEmpty { livFolder.id },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Info rows
                DetailRow("Folder ID", livFolder.id, monospace = true)
                DetailRow("Path", livFolder.path)
                DetailRow("Type", when (livFolder.type) {
                    "sendreceive" -> "Send & Receive"
                    "sendonly"    -> "Send Only"
                    "receiveonly" -> "Receive Only"
                    else          -> livFolder.type.ifEmpty { "Send & Receive" }
                })
                DetailRow("Rescan Interval", "${livFolder.rescanIntervalS}s")
                DetailRow("File Watcher", if (livFolder.fsWatcherEnabled) "Enabled" else "Disabled")
                DetailRow("Versioning", livFolder.versioning?.type?.ifEmpty { "none" }?.replaceFirstChar { it.uppercase() } ?: "None")

                DetailRow("Shared With", "${livFolder.devices.size} device${if (livFolder.devices.size != 1) "s" else ""}")
                DetailRow("Status",
                    if (livFolder.paused) "Paused" else "Active",
                    valueColor = if (livFolder.paused)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.primary
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pause / Resume
                    OutlinedButton(
                        onClick = { viewModel.pauseFolder(livFolder.id, !livFolder.paused) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (livFolder.paused) "Resume" else "Pause")
                    }
                    // Rescan
                    OutlinedButton(
                        onClick = { viewModel.rescanFolder(livFolder.id) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Rescan")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Open in Explorer
                    OutlinedButton(
                        onClick = onOpenInExplorer,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Open Folder")
                    }
                    // Delete (red)
                    Button(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    monospace: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
