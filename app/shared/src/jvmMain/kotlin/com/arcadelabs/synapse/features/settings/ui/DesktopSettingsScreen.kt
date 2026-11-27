package com.arcadelabs.synapse.features.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arcadelabs.synapse.DesktopQrCodeView
import com.arcadelabs.synapse.core.designsystem.SynapseIcons
import com.arcadelabs.synapse.core.designsystem.SynapseStatusColors
import com.arcadelabs.synapse.core.designsystem.components.SettingsContentPadding
import com.arcadelabs.synapse.core.designsystem.components.SettingsGroup
import com.arcadelabs.synapse.core.designsystem.components.SettingsSectionHeader
import com.arcadelabs.synapse.core.designsystem.components.SettingsSwitchItem
import com.arcadelabs.synapse.core.designsystem.components.SettingsTextField
import com.arcadelabs.synapse.core.designsystem.components.SettingsThemeItem
import com.arcadelabs.synapse.core.designsystem.components.SettingsThemeModeItem
import com.arcadelabs.synapse.core.network.SynapseApiClient
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.koin.compose.viewmodel.koinViewModel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun DesktopSettingsScreen(
    apiClient: SynapseApiClient,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with Save button
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Only the legacy engine has settings saved through this button; everything
                // shown for Synapse Core (appearance, start on boot) is stored as it changes.
                if (state.isLegacyEngine) {
                    Button(
                        onClick = { viewModel.saveSettings() },
                        enabled = !state.isSaving && !state.isLoading && state.error == null,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current
                            )
                        } else {
                            Icon(SynapseIcons.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        }
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Save")
                    }
                }
            }

            // Device identity
            SettingsGroup(
                title = "This device",
                subtitle = "Other devices need this ID to connect and share folders with you."
            ) {
                Row(
                    modifier = SettingsContentPadding.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.deviceId.isNotEmpty()) {
                        val qrText = if (state.deviceName.isNotEmpty()) {
                            "syncthing://${state.deviceId}?name=${state.deviceName}"
                        } else {
                            state.deviceId
                        }
                        // QR codes need fixed dark-on-white to scan reliably.
                        Surface(color = Color.White, shape = MaterialTheme.shapes.small) {
                            DesktopQrCodeView(
                                text = qrText,
                                modifier = Modifier.size(120.dp).padding(8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Device ID",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SelectionContainer {
                                Text(
                                    text = state.deviceId.ifEmpty { "Loading…" },
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
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
                            enabled = state.deviceId.isNotEmpty(),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                        ) {
                            Icon(
                                if (isCopyClicked) SynapseIcons.Check else SynapseIcons.Copy,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                            Text(if (isCopyClicked) "Copied" else "Copy ID")
                        }
                    }
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null) {
                SettingsGroup(title = "Couldn't load settings") {
                    Column(modifier = SettingsContentPadding) {
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
                }
            } else {
                // Each setting is shown only where it takes effect: engine settings need the
                // legacy engine (Synapse Core has no API for them), and start-on-boot is only
                // implemented for Windows (see setWindowsStartup in desktopApp main.kt).
                val legacy = state.isLegacyEngine
                val startOnBootSupported = remember {
                    System.getProperty("os.name").orEmpty().lowercase().contains("win")
                }
                if (legacy || startOnBootSupported) {
                    SettingsSectionHeader("General")
                    SettingsGroup {
                        if (legacy) {
                            SettingsTextField(
                                label = "Device name",
                                value = state.deviceName,
                                onValueChange = { viewModel.updateDeviceName(it) },
                                supportingText = "How this device appears to others",
                                modifier = SettingsContentPadding
                            )
                        }
                        if (startOnBootSupported) {
                            SettingsSwitchItem(
                                label = "Start on boot",
                                description = "Launch Synapse when you sign in to your computer",
                                checked = state.autoStart,
                                onCheckedChange = { viewModel.updateAutoStart(it) }
                            )
                        }
                        if (legacy) {
                            SettingsSwitchItem(
                                label = "Usage reporting",
                                description = "Send anonymous usage data",
                                checked = state.urAccepted > 0,
                                onCheckedChange = { viewModel.updateUsageReporting(it) }
                            )
                        }
                    }
                }

                SettingsSectionHeader("Appearance")
                SettingsGroup {
                    SettingsThemeItem(
                        selectedTheme = state.selectedTheme,
                        onThemeSelected = { viewModel.updateSelectedTheme(it) }
                    )
                    SettingsThemeModeItem(
                        selectedMode = state.themeMode,
                        onModeSelected = { viewModel.updateThemeMode(it) }
                    )
                }

                if (legacy) {
                    SettingsSectionHeader("Connections")
                    SettingsGroup {
                        SettingsTextField(
                            label = "Listen addresses",
                            value = state.listenAddresses,
                            onValueChange = { viewModel.updateListenAddresses(it) },
                            supportingText = "Comma-separated, e.g. default, tcp://0.0.0.0:22000",
                            modifier = SettingsContentPadding
                        )
                        Row(
                            modifier = SettingsContentPadding.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SettingsTextField(
                                label = "Download limit (KiB/s)",
                                value = state.maxRecvKbps,
                                onValueChange = { viewModel.updateMaxRecvKbps(it) },
                                keyboardType = KeyboardType.Number,
                                supportingText = "0 = unlimited",
                                modifier = Modifier.weight(1f)
                            )
                            SettingsTextField(
                                label = "Upload limit (KiB/s)",
                                value = state.maxSendKbps,
                                onValueChange = { viewModel.updateMaxSendKbps(it) },
                                keyboardType = KeyboardType.Number,
                                supportingText = "0 = unlimited",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        SettingsSwitchItem(
                            label = "NAT traversal",
                            description = "Try to connect through routers and firewalls",
                            checked = state.natEnabled,
                            onCheckedChange = { viewModel.updateNatEnabled(it) }
                        )
                        SettingsSwitchItem(
                            label = "Relaying",
                            description = "Use relay servers when a direct connection isn't possible",
                            checked = state.relaysEnabled,
                            onCheckedChange = { viewModel.updateRelaysEnabled(it) }
                        )
                        SettingsSwitchItem(
                            label = "Local discovery",
                            description = "Find devices on the local network",
                            checked = state.localAnnounceEnabled,
                            onCheckedChange = { viewModel.updateLocalAnnounceEnabled(it) }
                        )
                        SettingsSwitchItem(
                            label = "Global discovery",
                            description = "Find devices through discovery servers",
                            checked = state.globalAnnounceEnabled,
                            onCheckedChange = { viewModel.updateGlobalAnnounceEnabled(it) }
                        )
                        SettingsTextField(
                            label = "Discovery servers",
                            value = state.globalAnnounceServers,
                            onValueChange = { viewModel.updateGlobalAnnounceServers(it) },
                            supportingText = "Comma-separated server URLs",
                            modifier = SettingsContentPadding
                        )
                    }

                    SettingsSectionHeader("Web GUI")
                    SettingsGroup {
                        Row(
                            modifier = SettingsContentPadding.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SettingsTextField(
                                label = "Listen address",
                                value = state.guiAddress,
                                onValueChange = { viewModel.updateGuiAddress(it) },
                                supportingText = "Address and port, e.g. 127.0.0.1:8384",
                                modifier = Modifier.weight(1f)
                            )
                            SettingsTextField(
                                label = "Username",
                                value = state.guiUser,
                                onValueChange = { viewModel.updateGuiUser(it) },
                                supportingText = "Leave empty to turn off sign-in",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        SettingsSwitchItem(
                            label = "Use HTTPS",
                            description = "Requires TLS certificates",
                            checked = state.guiUseTLS,
                            onCheckedChange = { viewModel.updateGuiUseTLS(it) }
                        )
                        Box(modifier = SettingsContentPadding) {
                            OutlinedButton(
                                onClick = { openUrl?.invoke("http://127.0.0.1:8384") },
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                            ) {
                                Icon(SynapseIcons.Web, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                                Text("Open Web GUI")
                            }
                        }
                    }

                    SettingsSectionHeader("Advanced")
                    SettingsGroup {
                        SettingsSwitchItem(
                            label = "Crash reporting",
                            description = "Send crash reports to help fix problems",
                            checked = state.crashReportingEnabled,
                            onCheckedChange = { viewModel.updateCrashReportingEnabled(it) }
                        )
                    }
                }
            }

            SettingsSectionHeader("Import and export")
            SettingsGroup(
                subtitle = null
            ) {
                Column(modifier = SettingsContentPadding) {
                    Text(
                        "View, copy or replace the raw JSON configuration.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
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
                            color = if (isImportSuccess) SynapseStatusColors.Connected else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        Json.parseToJsonElement(configJsonText)
                                        apiClient.updateRawSystemConfig(configJsonText)
                                        isImportSuccess = true
                                        importStatusMessage = "Configuration imported"
                                    } catch(e: Exception) {
                                        isImportSuccess = false
                                        val msg = e.message ?: ""
                                        importStatusMessage = if (
                                            msg.contains("connect", ignoreCase = true) ||
                                            msg.contains("127.0.0.1") ||
                                            msg.contains("refused", ignoreCase = true)
                                        ) {
                                            "Synapse isn't running"
                                        } else {
                                            "Couldn't import: $msg"
                                        }
                                    }
                                }
                            }
                        ) {
                            Text("Import")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        }
    }
}
