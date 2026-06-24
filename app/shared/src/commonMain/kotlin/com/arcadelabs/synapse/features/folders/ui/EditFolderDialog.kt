package com.arcadelabs.synapse.features.folders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arcadelabs.synapse.SynapseBackHandler
import com.arcadelabs.synapse.core.designsystem.FolderIcon
import com.arcadelabs.synapse.core.domain.models.Folder
import com.arcadelabs.synapse.core.domain.models.normalizeDeviceId
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFolderDialog(
    folder: Folder,
    onDismiss: () -> Unit,
    selectDirectory: ((onPathSelected: (String) -> Unit) -> Unit)? = null,
    viewModel: FolderViewModel = koinViewModel()
) {
    val devices by viewModel.devicesState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var folderLabel by remember { mutableStateOf(folder.label) }
    var folderPath by remember { mutableStateOf(folder.path) }
    val selectedDevices = remember {
        mutableStateListOf<String>().apply {
            addAll(folder.devices.map { it.deviceID.normalizeDeviceId() })
        }
    }
    var folderType by remember { mutableStateOf(folder.type) }
    var watchForChanges by remember { mutableStateOf(folder.fsWatcherEnabled) }
    var pauseFolder by remember { mutableStateOf(folder.paused) }
    var versioningType by remember { mutableStateOf(folder.versioning.type) }
    var cleanoutAfter by remember {
        mutableStateOf(
            folder.versioning.params["cleanoutAfter"]?.toIntOrNull() ?: 0
        )
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    val canSave = folderPath.isNotBlank()

    SynapseBackHandler(enabled = true, onBack = onDismiss)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Folder", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (canSave) {
                                viewModel.updateFolder(
                                    id = folder.id,
                                    label = folderLabel.trim(),
                                    path = folderPath.trim(),
                                    type = folderType,
                                    paused = pauseFolder,
                                    watchForChanges = watchForChanges,
                                    sharedDevices = selectedDevices.toList(),
                                    versioningType = versioningType,
                                    cleanoutAfter = cleanoutAfter,
                                    onSuccess = onDismiss
                                )
                            }
                        },
                        enabled = canSave && !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "Save")
                        }
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
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

            // Folder ID (Read-only)
            OutlinedTextField(
                value = folder.id,
                onValueChange = {},
                readOnly = true,
                label = { Text("Folder ID (Cannot be changed)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    disabledBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
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
                    fontWeight = FontWeight.SemiBold
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

            // Cleanout After Scrollable Selector
            if (versioningType == "trashcan" || versioningType == "staggered") {
                Text(
                    text = "Cleanout After (Days)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (cleanoutAfter == 0) "Never (0)" else "$cleanoutAfter days",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(90.dp)
                    )
                    
                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, cleanoutAfter - 2))
                    LazyRow(
                        state = listState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        items(91) { index ->
                            val isSelected = index == cleanoutAfter
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { cleanoutAfter = index },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (index == 0) "∞" else index.toString(),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Delete Folder Button
            Button(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Folder")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Folder?") },
            text = {
                Text("Remove \"${folder.label.ifEmpty { folder.id }}\" from Syncthing?\n\nThis will stop syncing but will NOT delete the files on disk.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFolder(folder.id)
                        showDeleteConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
