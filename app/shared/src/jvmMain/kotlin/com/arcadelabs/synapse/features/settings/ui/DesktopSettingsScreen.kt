package com.arcadelabs.synapse.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arcadelabs.synapse.DesktopQrCodeView
import com.arcadelabs.synapse.core.network.SyncthingApiClient
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopSettingsScreen(
    apiClient: SyncthingApiClient,
    openUrl: ((String) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var localDeviceId by remember { mutableStateOf("") }
    var configJsonText by remember { mutableStateOf("") }
    var importStatusMessage by remember { mutableStateOf<String?>(null) }
    var isImportSuccess by remember { mutableStateOf(false) }
    var isCopyClicked by remember { mutableStateOf(false) }

    // Load Device ID and Raw Config
    LaunchedEffect(Unit) {
        try {
            val status = apiClient.systemStatus()
            localDeviceId = status.myID
        } catch (e: Exception) {
            localDeviceId = "Error retrieving ID"
        }

        try {
            val rawConfig = apiClient.rawSystemConfig()
            val json = kotlinx.serialization.json.Json { prettyPrint = true }
            val jsonElement = json.parseToJsonElement(rawConfig)
            configJsonText = json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), jsonElement)
        } catch (e: Exception) {
            configJsonText = "Error loading config: ${e.message}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Device Identity Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Device Identity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This device's ID is shown below. Other devices need this ID to connect and sync folders with you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (localDeviceId.isNotEmpty() && localDeviceId != "Error retrieving ID") {
                        DesktopQrCodeView(
                            text = localDeviceId,
                            modifier = Modifier
                                .size(120.dp)
                                .background(Color.White, shape = RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "My Device ID",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SelectionContainer {
                            Text(
                                text = localDeviceId.ifEmpty { "Loading..." },
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp)
                                    .fillMaxWidth()
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            Button(
                                onClick = {
                                    if (localDeviceId.isNotEmpty() && localDeviceId != "Error retrieving ID") {
                                        try {
                                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                            val selection = StringSelection(localDeviceId)
                                            clipboard.setContents(selection, selection)
                                            isCopyClicked = true
                                        } catch (e: Exception) {
                                            // Handle clipboard error if any
                                        }
                                    }
                                },
                                enabled = localDeviceId.isNotEmpty() && localDeviceId != "Error retrieving ID"
                            ) {
                                Text(if (isCopyClicked) "Copied!" else "Copy ID")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Import / Export Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Directly view or modify the raw JSON configuration of your Syncthing instance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = configJsonText,
                    onValueChange = {
                        configJsonText = it
                        importStatusMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    label = { Text("Configuration JSON") }
                )

                if (importStatusMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = importStatusMessage!!,
                        color = if (isImportSuccess) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    kotlinx.serialization.json.Json.parseToJsonElement(configJsonText)
                                    apiClient.updateRawSystemConfig(configJsonText)
                                    isImportSuccess = true
                                    importStatusMessage = "Configuration imported successfully!"
                                } catch(e: Exception) {
                                    isImportSuccess = false
                                    val msg = e.message ?: ""
                                    importStatusMessage = if (
                                        msg.contains("connect", ignoreCase = true) ||
                                        msg.contains("127.0.0.1") ||
                                        msg.contains("refused", ignoreCase = true)
                                    ) {
                                        "Error: Daemon is not running"
                                    } else {
                                        "Error importing config: $msg"
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Import Configuration")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Web GUI Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Advanced Integrations",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Open the official Syncthing Web GUI directly in your default web browser for advanced settings and diagnostics.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { openUrl?.invoke("http://127.0.0.1:8384") }
                ) {
                    Text("Open Web GUI")
                }
            }
        }
    }
}
