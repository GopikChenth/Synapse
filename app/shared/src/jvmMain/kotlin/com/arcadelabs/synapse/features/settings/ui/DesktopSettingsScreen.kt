package com.arcadelabs.synapse.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arcadelabs.synapse.DesktopQrCodeView
import com.arcadelabs.synapse.core.network.SyncthingApiClient
import com.arcadelabs.synapse.core.domain.models.normalizeDeviceId
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.koin.compose.viewmodel.koinViewModel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopSettingsScreen(
    apiClient: SyncthingApiClient,
    openUrl: ((String) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsState()

    // Raw config state (kept for import/export card)
    var configJsonText by remember { mutableStateOf("") }
    var importStatusMessage by remember { mutableStateOf<String?>(null) }
    var isImportSuccess by remember { mutableStateOf(false) }
    var isCopyClicked by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Load raw config
    LaunchedEffect(Unit) {
        try {
            val rawConfig = apiClient.rawSystemConfig()
            val json = Json { prettyPrint = true }
            val jsonElement = json.parseToJsonElement(rawConfig)
            configJsonText = json.encodeToString(JsonElement.serializer(), jsonElement)
        } catch (e: Exception) {
            configJsonText = "Error loading config: ${e.message}"
        }
    }

    // Show save feedback
    LaunchedEffect(state.saveSuccess, state.saveError) {
        if (state.saveSuccess) {
            snackbarHostState.showSnackbar("Settings saved successfully")
            viewModel.clearSaveStatus()
        }
        state.saveError?.let {
            snackbarHostState.showSnackbar("Save failed: $it")
            viewModel.clearSaveStatus()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header with Save button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Button(
                    onClick = { viewModel.saveSettings() },
                    enabled = !state.isSaving && !state.isLoading && state.error == null
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Save Settings")
                }
            }

            // Device Identity Card
            DesktopSettingsCard(title = "Device Identity", subtitle = "This device's ID is shown below. Other devices need this ID to connect and sync folders with you.") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.deviceId.isNotEmpty()) {
                        val qrText = if (state.deviceName.isNotEmpty()) {
                            "syncthing://${state.deviceId}?name=${state.deviceName}"
                        } else {
                            state.deviceId
                        }
                        DesktopQrCodeView(
                            text = qrText,
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
                                text = state.deviceId.ifEmpty { "Loading..." },
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
                                    if (state.deviceId.isNotEmpty()) {
                                        try {
                                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                            val selection = StringSelection(state.deviceId)
                                            clipboard.setContents(selection, selection)
                                            isCopyClicked = true
                                        } catch (_: Exception) {}
                                    }
                                },
                                enabled = state.deviceId.isNotEmpty()
                            ) {
                                Text(if (isCopyClicked) "Copied!" else "Copy ID")
                            }
                        }
                    }
                }
            }

            // Loading / Error state for settings
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null) {
                DesktopSettingsCard(title = "Error") {
                    Text(
                        text = state.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.loadSettings() }) {
                        Text("Retry")
                    }
                }
            } else {
                // --- General Section ---
                DesktopSectionHeader("General")

                DesktopSettingsCard(title = "General Options") {
                    DesktopSettingsTextField(
                        label = "Device Name",
                        value = state.deviceName,
                        onValueChange = { viewModel.updateDeviceName(it) },
                        supportingText = "The name by which this device is known to others"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    DesktopSettingsSwitchRow(
                        label = "Usage Reporting",
                        description = "Allow anonymous usage data to be sent to the Syncthing developers",
                        checked = state.urAccepted > 0,
                        onCheckedChange = { viewModel.updateUsageReporting(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    DesktopThemeSelectionRow(
                        selectedTheme = state.selectedTheme,
                        onThemeSelected = { viewModel.updateSelectedTheme(it) }
                    )
                }

                // --- Connections Section ---
                DesktopSectionHeader("Connections")

                DesktopSettingsCard(title = "Connection Settings") {
                    DesktopSettingsTextField(
                        label = "Listen Addresses",
                        value = state.listenAddresses,
                        onValueChange = { viewModel.updateListenAddresses(it) },
                        supportingText = "Comma-separated list (e.g. default, tcp://0.0.0.0:22000)"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DesktopSettingsTextField(
                            label = "Incoming Rate Limit (kbps)",
                            value = state.maxRecvKbps,
                            onValueChange = { viewModel.updateMaxRecvKbps(it) },
                            keyboardType = KeyboardType.Number,
                            supportingText = "0 = unlimited",
                            modifier = Modifier.weight(1f)
                        )
                        DesktopSettingsTextField(
                            label = "Outgoing Rate Limit (kbps)",
                            value = state.maxSendKbps,
                            onValueChange = { viewModel.updateMaxSendKbps(it) },
                            keyboardType = KeyboardType.Number,
                            supportingText = "0 = unlimited",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Toggle row with 2 columns
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            DesktopSettingsSwitchRow(
                                label = "NAT Traversal",
                                description = "Attempt to punch through NATs",
                                checked = state.natEnabled,
                                onCheckedChange = { viewModel.updateNatEnabled(it) }
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            DesktopSettingsSwitchRow(
                                label = "Relaying",
                                description = "Use relay servers when direct connection fails",
                                checked = state.relaysEnabled,
                                onCheckedChange = { viewModel.updateRelaysEnabled(it) }
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            DesktopSettingsSwitchRow(
                                label = "Local Discovery",
                                description = "Discover devices on the local network",
                                checked = state.localAnnounceEnabled,
                                onCheckedChange = { viewModel.updateLocalAnnounceEnabled(it) }
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            DesktopSettingsSwitchRow(
                                label = "Global Discovery",
                                description = "Register and look up via discovery servers",
                                checked = state.globalAnnounceEnabled,
                                onCheckedChange = { viewModel.updateGlobalAnnounceEnabled(it) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    DesktopSettingsTextField(
                        label = "Global Discovery Servers",
                        value = state.globalAnnounceServers,
                        onValueChange = { viewModel.updateGlobalAnnounceServers(it) },
                        supportingText = "Comma-separated list of discovery server URLs"
                    )
                }

                // --- Web GUI Section ---
                DesktopSectionHeader("Web GUI")

                DesktopSettingsCard(title = "GUI Configuration") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DesktopSettingsTextField(
                            label = "GUI Listen Address",
                            value = state.guiAddress,
                            onValueChange = { viewModel.updateGuiAddress(it) },
                            supportingText = "Address and port (e.g. 127.0.0.1:8384)",
                            modifier = Modifier.weight(1f)
                        )
                        DesktopSettingsTextField(
                            label = "GUI Username",
                            value = state.guiUser,
                            onValueChange = { viewModel.updateGuiUser(it) },
                            supportingText = "Leave empty for no authentication",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    DesktopSettingsSwitchRow(
                        label = "Use HTTPS",
                        description = "Enable HTTPS for the GUI (requires TLS certificates)",
                        checked = state.guiUseTLS,
                        onCheckedChange = { viewModel.updateGuiUseTLS(it) }
                    )
                }

                // --- Advanced Section ---
                DesktopSectionHeader("Advanced")

                DesktopSettingsCard(title = "Advanced Options") {
                    DesktopSettingsSwitchRow(
                        label = "Crash Reporting",
                        description = "Send crash reports to help improve Syncthing",
                        checked = state.crashReportingEnabled,
                        onCheckedChange = { viewModel.updateCrashReportingEnabled(it) }
                    )
                }
            }

            // Configuration Import/Export Card (always shown)
            DesktopSettingsCard(
                title = "Import / Export Configuration",
                subtitle = "Directly view or modify the raw JSON configuration of your Syncthing instance."
            ) {
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
                                    Json.parseToJsonElement(configJsonText)
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

            // Web GUI Card (always shown)
            DesktopSettingsCard(
                title = "Advanced Integrations",
                subtitle = "Open the official Syncthing Web GUI directly in your default web browser for advanced settings and diagnostics."
            ) {
                Button(
                    onClick = { openUrl?.invoke("http://127.0.0.1:8384") }
                ) {
                    Text("Open Web GUI")
                }
            }
        }
    }
}

// --- Desktop-specific reusable components ---

@Composable
private fun DesktopSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun DesktopSettingsCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun DesktopSettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    )
}

@Composable
private fun DesktopSettingsSwitchRow(
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun DesktopThemeSelectionRow(
    selectedTheme: String,
    onThemeSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val themes = listOf(
        "Default" to "Standard (Material)",
        "DeepSpace" to "Deep Space (Sleek Dark)",
        "SunsetGlow" to "Sunset Glow (Warm Amber)",
        "NordicForest" to "Nordic Forest (Emerald Mint)"
    )
    val selectedLabel = themes.find { it.first == selectedTheme }?.second ?: "Standard (Material)"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "App Theme",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Select premium interface theme",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(selectedLabel, color = MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                themes.forEach { (themeKey, themeLabel) ->
                    DropdownMenuItem(
                        text = { Text(themeLabel) },
                        onClick = {
                            onThemeSelected(themeKey)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

