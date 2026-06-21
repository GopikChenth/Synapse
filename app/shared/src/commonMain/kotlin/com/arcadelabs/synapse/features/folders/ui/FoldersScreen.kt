package com.arcadelabs.synapse.features.folders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadelabs.synapse.core.designsystem.FolderIcon
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
    openFolder: ((String) -> Unit)? = null,
    viewModel: FolderViewModel = koinViewModel()
) {
    val folders by viewModel.foldersState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold { paddingValues ->
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
                        text = "No folders shared",
                        style = MaterialTheme.typography.bodyMedium,
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
                            FolderCard(
                                folder = folder,
                                onOpenClick = { openFolder?.invoke(folder.path) }
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
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    selectDirectory: ((onPathSelected: (String) -> Unit) -> Unit)? = null,
    viewModel: FolderViewModel = koinViewModel()
) {
    val devices by viewModel.devicesState.collectAsState()
    val filteredDevices = remember(devices) {
        devices.filter { it.name.lowercase() != "localhost" }
    }

    // Form States
    var folderLabel by remember { mutableStateOf("") }
    var folderId by remember { mutableStateOf(generateRandomFolderId()) }
    var folderPath by remember { mutableStateOf("") }
    val selectedDevices = remember { mutableStateListOf<String>() }
    var folderType by remember { mutableStateOf("sendreceive") }
    var watchForChanges by remember { mutableStateOf(true) }
    var pauseFolder by remember { mutableStateOf(false) }
    var versioningType by remember { mutableStateOf("none") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Folder", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (folderId.isNotEmpty() && folderPath.isNotEmpty()) {
                                viewModel.createFolder(
                                    id = folderId,
                                    label = folderLabel,
                                    path = folderPath,
                                    type = folderType,
                                    paused = pauseFolder,
                                    watchForChanges = watchForChanges,
                                    sharedDevices = selectedDevices.toList(),
                                    versioningType = versioningType,
                                    onSuccess = onDismiss
                                )
                            }
                        },
                        enabled = folderId.isNotEmpty() && folderPath.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 1. Folder Label
            FormField(icon = Icons.Default.Edit, title = "Folder Label") {
                OutlinedTextField(
                    value = folderLabel,
                    onValueChange = { folderLabel = it },
                    label = { Text("Folder Label", style = MaterialTheme.typography.bodyMedium) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 2. Folder ID
            FormField(icon = Icons.Default.Info, title = "Folder ID") {
                OutlinedTextField(
                    value = folderId,
                    onValueChange = { folderId = it },
                    label = { Text("Folder ID", style = MaterialTheme.typography.bodyMedium) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 3. Directory Path (with trailing folder picker icon)
            FormField(icon = FolderIcon, title = "Directory") {
                OutlinedTextField(
                    value = folderPath,
                    onValueChange = { folderPath = it },
                    label = { Text("Directory", style = MaterialTheme.typography.bodyMedium) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            selectDirectory?.invoke { selectedPath ->
                                folderPath = selectedPath
                            }
                        }) {
                            Icon(FolderIcon, contentDescription = "Select Directory", modifier = Modifier.size(20.dp))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 4. Devices checklist
            FormField(icon = Icons.Default.Share, title = "Devices") {
                Text(
                    text = "Devices",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (filteredDevices.isEmpty()) {
                    Text(
                        text = "No devices configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    filteredDevices.forEach { device ->
                        val isChecked = selectedDevices.contains(device.deviceID)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) {
                                        selectedDevices.remove(device.deviceID)
                                    } else {
                                        selectedDevices.add(device.deviceID)
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = device.name.ifEmpty { device.deviceID.take(7) },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedDevices.add(device.deviceID)
                                    } else {
                                        selectedDevices.remove(device.deviceID)
                                    }
                                },
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                    }
                }
            }

            // 5. Folder Type Selection
            FormField(icon = Icons.Default.Lock, title = "Folder Type") {
                var showTypeDropdown by remember { mutableStateOf(false) }
                val typeLabel = when (folderType) {
                    "sendreceive" -> "Send & Receive"
                    "sendonly" -> "Send Only"
                    "receiveonly" -> "Receive Only"
                    else -> "Send & Receive"
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Folder Type",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTypeDropdown = true }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    DropdownMenu(
                        expanded = showTypeDropdown,
                        onDismissRequest = { showTypeDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Send & Receive", style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                folderType = "sendreceive"
                                showTypeDropdown = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Send Only", style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                folderType = "sendonly"
                                showTypeDropdown = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Receive Only", style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                folderType = "receiveonly"
                                showTypeDropdown = false
                            }
                        )
                    }
                    
                    val description = when (folderType) {
                        "sendreceive" -> "The folder will both send changes to and receive changes from remote devices."
                        "sendonly" -> "The folder will only send changes to remote devices, ignoring any incoming modifications."
                        "receiveonly" -> "The folder will only receive changes from remote devices, never uploading local changes."
                        else -> ""
                    }
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 6. Watch for changes
            FormField(icon = Icons.Default.Refresh, title = "Watch for changes") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Watch for changes",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Asks operating system to notify about changes to files. If disabled falls back to periodic hourly scans.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = watchForChanges,
                        onCheckedChange = { watchForChanges = it },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }

            // 7. Pause Folder
            FormField(
                icon = Icons.Default.Warning,
                title = "Pause Folder",
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pause Folder",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = pauseFolder,
                        onCheckedChange = { pauseFolder = it },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }

            // 8. File Versioning
            FormField(icon = Icons.Default.Refresh, title = "File Versioning") {
                var showVersioningDropdown by remember { mutableStateOf(false) }
                val versioningLabel = when (versioningType) {
                    "none" -> "No File Versioning"
                    "trashcan" -> "Trashcan File Versioning"
                    "simple" -> "Simple File Versioning"
                    "staggered" -> "Staggered File Versioning"
                    else -> "No File Versioning"
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "File Versioning",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showVersioningDropdown = true }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = versioningLabel,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    DropdownMenu(
                        expanded = showVersioningDropdown,
                        onDismissRequest = { showVersioningDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("No File Versioning", style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                versioningType = "none"
                                showVersioningDropdown = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Trashcan File Versioning", style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                versioningType = "trashcan"
                                showVersioningDropdown = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Simple File Versioning", style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                versioningType = "simple"
                                showVersioningDropdown = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Staggered File Versioning", style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                versioningType = "staggered"
                                showVersioningDropdown = false
                            }
                        )
                    }
                    
                    Text(
                        text = "Syncthing supports archiving the old version of a file when it is deleted or replaced with a newer version from the cluster. Click to choose versioning strategy.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

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
        Column(
            modifier = Modifier.weight(1f)
        ) {
            content()
        }
    }
}

@Composable
fun FolderCard(
    folder: com.arcadelabs.synapse.core.domain.models.Folder,
    onOpenClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenClick() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.label.ifEmpty { folder.id },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "❖ ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = folder.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            imageVector = FolderIcon,
            contentDescription = "Open in File Manager",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}
