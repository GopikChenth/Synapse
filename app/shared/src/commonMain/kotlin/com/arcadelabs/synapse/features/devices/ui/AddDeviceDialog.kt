@file:Suppress("DEPRECATION")
package com.arcadelabs.synapse.features.devices.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadelabs.synapse.core.designsystem.DevicesIcon
import com.arcadelabs.synapse.core.designsystem.FolderIcon
import com.arcadelabs.synapse.core.designsystem.QrCodeIcon
import org.koin.compose.viewmodel.koinViewModel
import com.arcadelabs.synapse.core.domain.models.parseSyncthingQr
import com.arcadelabs.synapse.core.domain.models.parseSyncthingQrDetails
import com.arcadelabs.synapse.SynapseBackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceDialog(
    onDismiss: () -> Unit,
    scanQrCode: ((onQrScanned: (String) -> Unit) -> Unit)? = null,
    prefilledDeviceId: String = "",
    prefilledDeviceName: String = "",
    viewModel: DeviceViewModel = koinViewModel()
) {
    SynapseBackHandler(enabled = true, onBack = onDismiss)
    val folders by viewModel.folders.collectAsState()

    // Form States
    var deviceId by remember { mutableStateOf(prefilledDeviceId) }
    var deviceName by remember { mutableStateOf(prefilledDeviceName) }
    var deviceAddresses by remember { mutableStateOf("dynamic") }
    val selectedFolders = remember { mutableStateListOf<String>() }
    var isIntroducer by remember { mutableStateOf(false) }
    var isAutoAccept by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var isUntrusted by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Device", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (deviceId.isNotEmpty()) {
                                val addressList = deviceAddresses.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                viewModel.createDevice(
                                    id = deviceId,
                                    name = deviceName,
                                    addresses = addressList,
                                    introducer = isIntroducer,
                                    autoAccept = isAutoAccept,
                                    paused = isPaused,
                                    untrusted = isUntrusted,
                                    sharedFolders = selectedFolders.toList(),
                                    onSuccess = onDismiss
                                )
                            }
                        },
                        enabled = deviceId.isNotEmpty()
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
        val error by viewModel.error.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (error != null) {
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            // 1. Device ID (with trailing QR code scanner icon)
            FormField(icon = Icons.Default.Info, title = "Device ID") {
                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { input ->
                        if (input.startsWith("syncthing://", ignoreCase = true)) {
                            val (parsedId, parsedName) = input.parseSyncthingQrDetails()
                            deviceId = parsedId
                            if (parsedName.isNotEmpty()) {
                                deviceName = parsedName
                            }
                        } else {
                            deviceId = input
                        }
                    },
                    label = { Text("Device ID", style = MaterialTheme.typography.bodyMedium) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    trailingIcon = {
                        if (scanQrCode != null) {
                            IconButton(onClick = {
                                scanQrCode.invoke { scannedId ->
                                    val (parsedId, parsedName) = scannedId.parseSyncthingQrDetails()
                                    deviceId = parsedId
                                    if (parsedName.isNotEmpty()) {
                                        deviceName = parsedName
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = QrCodeIcon,
                                    contentDescription = "Scan QR Code",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 2. Name
            FormField(icon = Icons.Default.Edit, title = "Name") {
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("Name", style = MaterialTheme.typography.bodyMedium) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 3. Addresses
            FormField(icon = Icons.Default.Share, title = "Addresses") {
                OutlinedTextField(
                    value = deviceAddresses,
                    onValueChange = { deviceAddresses = it },
                    label = { Text("Addresses", style = MaterialTheme.typography.bodyMedium) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 4. Folders Checklist
            FormField(icon = FolderIcon, title = "Folders") {
                Text(
                    text = "Folders",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (folders.isEmpty()) {
                    Text(
                        text = "No folders available to share",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    folders.forEach { folder ->
                        val isChecked = selectedFolders.contains(folder.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) {
                                        selectedFolders.remove(folder.id)
                                    } else {
                                        selectedFolders.add(folder.id)
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = folder.label.ifEmpty { folder.id },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedFolders.add(folder.id)
                                    } else {
                                        selectedFolders.remove(folder.id)
                                    }
                                },
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                    }
                }
            }

            // 5. Introducer
            FormField(
                icon = DevicesIcon,
                title = "Introducer",
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Introducer",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isIntroducer,
                        onCheckedChange = { isIntroducer = it },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }

            // 6. Auto Accept
            FormField(
                icon = Icons.Default.Refresh,
                title = "Auto Accept",
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Auto Accept",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isAutoAccept,
                        onCheckedChange = { isAutoAccept = it },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }

            // 7. Pause Device
            FormField(
                icon = Icons.Default.Warning,
                title = "Pause Device",
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pause Device",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isPaused,
                        onCheckedChange = { isPaused = it },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }

            // 8. Untrusted Device
            FormField(icon = Icons.Default.Lock, title = "Untrusted Device") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Untrusted Device",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
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
        }
    }
}

@Composable
private fun FormField(
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
