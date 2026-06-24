@file:Suppress("DEPRECATION")
package com.arcadelabs.synapse.features.devices.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadelabs.synapse.SynapseBackHandler
import com.arcadelabs.synapse.core.designsystem.FolderIcon
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDeviceDialog(
    device: DeviceUiModel,
    onDismiss: () -> Unit,
    viewModel: DeviceViewModel = koinViewModel()
) {
    SynapseBackHandler(enabled = true, onBack = onDismiss)
    val folders by viewModel.folders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    // Form States
    var deviceName by remember { mutableStateOf(device.name) }
    var deviceAddresses by remember { mutableStateOf(device.addresses.joinToString(", ")) }
    
    val selectedFolders = remember {
        mutableStateMapOf<String, Boolean>().apply {
            folders.forEach { folder ->
                val shared = folder.devices.any { it.deviceID.lowercase() == device.id.lowercase() }
                if (shared) {
                    put(folder.id, true)
                }
            }
        }
    }
    
    val folderPasswords = remember {
        mutableStateMapOf<String, String>().apply {
            folders.forEach { folder ->
                val ref = folder.devices.firstOrNull { it.deviceID.lowercase() == device.id.lowercase() }
                if (ref != null && ref.encryptionPassword.isNotEmpty()) {
                    put(folder.id, ref.encryptionPassword)
                }
            }
        }
    }

    var isIntroducer by remember { mutableStateOf(device.introducer) }
    var isAutoAccept by remember { mutableStateOf(device.autoAcceptFolders) }
    var isPaused by remember { mutableStateOf(device.paused) }
    var isUntrusted by remember { mutableStateOf(device.untrusted) }
    var customSyncConditions by remember { mutableStateOf(false) }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Device", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val addressList = deviceAddresses.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            
                            val sharedFoldersMap = selectedFolders.filter { it.value }.mapValues { (folderId, _) ->
                                folderPasswords[folderId] ?: ""
                            }
                            
                            viewModel.updateDevice(
                                id = device.id,
                                name = deviceName,
                                addresses = addressList,
                                introducer = isIntroducer,
                                autoAccept = isAutoAccept,
                                paused = isPaused,
                                untrusted = isUntrusted,
                                sharedFolders = sharedFoldersMap,
                                onSuccess = onDismiss
                            )
                        },
                        enabled = !isLoading
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
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Device")
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

            // 1. Device ID (Read-only) + Share Button
            OutlinedTextField(
                value = device.id,
                onValueChange = {},
                readOnly = true,
                label = { Text("Device ID") },
                trailingIcon = {
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(device.id))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Copy ID",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    disabledBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // 2. Name
            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text("Device Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 3. Addresses
            OutlinedTextField(
                value = deviceAddresses,
                onValueChange = { deviceAddresses = it },
                label = { Text("Addresses") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 4. Folders Section
            Text(
                text = "Folders",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            if (folders.isEmpty()) {
                Text(
                    text = "No folders available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                folders.forEach { folder ->
                    val isChecked = selectedFolders[folder.id] ?: false
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFolders[folder.id] = !isChecked
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    FolderIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = folder.label.ifEmpty { folder.id },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Switch(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selectedFolders[folder.id] = checked
                                },
                                modifier = Modifier.scale(0.8f)
                            )
                        }

                        if (isChecked) {
                            OutlinedTextField(
                                value = folderPasswords[folder.id] ?: "",
                                onValueChange = { pwd ->
                                    folderPasswords[folder.id] = pwd
                                },
                                label = { Text("If untrusted, enter encryption password", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 28.dp, bottom = 8.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 5. Introducer Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Introducer",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Switch(
                    checked = isIntroducer,
                    onCheckedChange = { isIntroducer = it },
                    modifier = Modifier.scale(0.8f)
                )
            }

            // 6. Auto Accept Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Auto Accept",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Switch(
                    checked = isAutoAccept,
                    onCheckedChange = { isAutoAccept = it },
                    modifier = Modifier.scale(0.8f)
                )
            }

            // 7. Pause Device Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Pause Device",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Switch(
                    checked = isPaused,
                    onCheckedChange = { isPaused = it },
                    modifier = Modifier.scale(0.8f)
                )
            }

            // 8. Untrusted Device Switch
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Untrusted Device",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Switch(
                        checked = isUntrusted,
                        onCheckedChange = { isUntrusted = it },
                        modifier = Modifier.scale(0.8f)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "All folders shared with this device must be protected by a password, such that all sent data is unreadable without the given password.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 9. Custom Sync Conditions Switch
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Custom sync conditions",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Switch(
                        checked = customSyncConditions,
                        onCheckedChange = { customSyncConditions = it },
                        modifier = Modifier.scale(0.8f)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Allows you to specify exceptions to the global run conditions.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Specify sync conditions",
                    color = if (customSyncConditions) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(enabled = customSyncConditions) {}
                        .padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Device?") },
            text = {
                Text("Are you sure you want to delete ${device.name.ifEmpty { "this device" }}?\n\nID: ${device.id.chunked(4).joinToString(" ")}")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDevice(device.id)
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
