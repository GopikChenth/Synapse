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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arcadelabs.synapse.core.designsystem.FolderIcon
import org.koin.compose.viewmodel.koinViewModel
import com.arcadelabs.synapse.core.domain.models.normalizeDeviceId

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
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
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
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(folders) { folder ->
                            FolderCard(
                                folder = folder,
                                onOpenClick = { openFolder?.invoke(folder.path) },
                                onDeleteFolder = { viewModel.deleteFolder(it) },
                                onRescanFolder = { viewModel.rescanFolder(it) }
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
fun CreateFolderSheet(
    onDismiss: () -> Unit,
    selectDirectory: ((onPathSelected: (String) -> Unit) -> Unit)? = null,
    prefilledFolderId: String = "",
    prefilledFolderLabel: String = "",
    prefilledSharedDevices: List<String> = emptyList(),
    viewModel: FolderViewModel = koinViewModel()
) {
    val devices by viewModel.devicesState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var folderLabel by remember { mutableStateOf(prefilledFolderLabel) }
    var folderId by remember { mutableStateOf(prefilledFolderId.ifEmpty { generateRandomFolderId() }) }
    var folderPath by remember { mutableStateOf("") }
    val selectedDevices = remember { mutableStateListOf<String>().apply { addAll(prefilledSharedDevices.map { it.normalizeDeviceId() }) } }
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
            // Header
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
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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

                // Folder Label
                OutlinedTextField(
                    value = folderLabel,
                    onValueChange = { folderLabel = it },
                    label = { Text("Folder Label (optional)") },
                    placeholder = { Text("e.g. My Documents") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Folder ID
                OutlinedTextField(
                    value = folderId,
                    onValueChange = { folderId = it },
                    label = { Text("Folder ID") },
                    singleLine = true,
                    supportingText = { Text("Must match the ID on remote devices to sync") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Directory Path
                OutlinedTextField(
                    value = folderPath,
                    onValueChange = { folderPath = it },
                    label = { Text("Directory Path") },
                    singleLine = true,
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
                    placeholder = { Text("/sdcard/Documents") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Folder Type
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
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

                // Share with Devices
                if (devices.isNotEmpty()) {
                    Text(
                        text = "Share with Devices",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    devices.forEach { device ->
                        val isChecked = selectedDevices.contains(device.deviceID.normalizeDeviceId())
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) selectedDevices.remove(device.deviceID.normalizeDeviceId())
                                    else selectedDevices.add(device.deviceID.normalizeDeviceId())
                                }
                                .padding(vertical = 6.dp),
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
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                    }
                }

                // Watch for Changes
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
                        modifier = Modifier.scale(0.85f)
                    )
                }

                // Pause Folder
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
                        modifier = Modifier.scale(0.85f)
                    )
                }

                // File Versioning
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
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

                // Bottom spacing
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// Keep old name as alias for backward compatibility with App.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    selectDirectory: ((onPathSelected: (String) -> Unit) -> Unit)? = null,
    prefilledFolderId: String = "",
    prefilledFolderLabel: String = "",
    prefilledSharedDevices: List<String> = emptyList(),
    viewModel: FolderViewModel = koinViewModel()
) = CreateFolderSheet(
    onDismiss = onDismiss,
    selectDirectory = selectDirectory,
    prefilledFolderId = prefilledFolderId,
    prefilledFolderLabel = prefilledFolderLabel,
    prefilledSharedDevices = prefilledSharedDevices,
    viewModel = viewModel
)

@Composable
fun FormField(
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
        Column(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
fun FolderCard(
    folder: com.arcadelabs.synapse.core.domain.models.Folder,
    onOpenClick: () -> Unit,
    onDeleteFolder: (String) -> Unit = {},
    onRescanFolder: (String) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Delete confirmation
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Folder?") },
            text = {
                Text(
                            "Remove \"${folder.label.ifEmpty { folder.id }}\" from Syncthing?\n\nThis will stop syncing but will NOT delete the files on disk."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteFolder(folder.id)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenClick() }
                .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
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
            val statusColor = if (folder.paused)
                MaterialTheme.colorScheme.tertiary
            else
                MaterialTheme.colorScheme.primary

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = statusColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = if (folder.paused) "Paused" else "Synced",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            // 3-dot menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    // Info header
                    Text(
                        text = "Folder Info",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("ID: ${folder.id}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Type: ${when(folder.type) {
                                        "sendreceive" -> "Send & Receive"
                                        "sendonly" -> "Send Only"
                                        "receiveonly" -> "Receive Only"
                                        else -> folder.type
                                    }}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text("Rescan: ${folder.rescanIntervalS}s", style = MaterialTheme.typography.bodySmall)
                                Text("Devices: ${folder.devices.size}", style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        onClick = {},
                        enabled = false
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Rescan Folder",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Rescan"
                            )
                        },
                        onClick = {
                            showMenu = false
                            onRescanFolder(folder.id)
                        }
                    )
                    HorizontalDivider()
                    // Delete
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Delete Folder",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }
    }
}

