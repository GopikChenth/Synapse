package com.arcadelabs.synapse.features.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arcadelabs.synapse.core.designsystem.SynapseIcons
import com.arcadelabs.synapse.core.designsystem.components.SettingsContentPadding
import com.arcadelabs.synapse.core.designsystem.components.SettingsGroup
import com.arcadelabs.synapse.core.designsystem.components.SettingsSectionHeader
import com.arcadelabs.synapse.core.designsystem.components.SettingsSwitchItem
import com.arcadelabs.synapse.core.designsystem.components.SettingsTextField
import com.arcadelabs.synapse.core.designsystem.components.SettingsThemeItem
import com.arcadelabs.synapse.core.designsystem.components.SettingsThemeModeItem
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    // Only platforms that actually start the app at boot (Android) pass true.
    supportsStartOnBoot: Boolean = false,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show save feedback via snackbar
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

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(SynapseIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(horizontal = 16.dp).size(24.dp)
                        )
                    } else if (state.isLegacyEngine) {
                        // Only the legacy engine has settings saved through this button.
                        TextButton(
                            onClick = { viewModel.saveSettings() },
                            enabled = !state.isLoading && state.error == null
                        ) {
                            Text("Save")
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.error ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { viewModel.loadSettings() }) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
                    SettingsContent(state = state, viewModel = viewModel, supportsStartOnBoot = supportsStartOnBoot)
                }
            }
        }
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    supportsStartOnBoot: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Each setting is shown only where it actually takes effect: engine settings need the
        // legacy engine (Synapse Core has no API for them), start-on-boot needs platform support.
        val legacy = state.isLegacyEngine
        if (legacy || supportsStartOnBoot) {
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
                if (supportsStartOnBoot) {
                    SettingsSwitchItem(
                        label = "Start on boot",
                        description = "Start syncing automatically when the device turns on",
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
                SettingsTextField(
                    label = "Download limit (KiB/s)",
                    value = state.maxRecvKbps,
                    onValueChange = { viewModel.updateMaxRecvKbps(it) },
                    keyboardType = KeyboardType.Number,
                    supportingText = "0 = unlimited",
                    modifier = SettingsContentPadding
                )
                SettingsTextField(
                    label = "Upload limit (KiB/s)",
                    value = state.maxSendKbps,
                    onValueChange = { viewModel.updateMaxSendKbps(it) },
                    keyboardType = KeyboardType.Number,
                    supportingText = "0 = unlimited",
                    modifier = SettingsContentPadding
                )
                SettingsSwitchItem(
                    label = "NAT traversal",
                    description = "Try to connect through routers and firewalls",
                    checked = state.natEnabled,
                    onCheckedChange = { viewModel.updateNatEnabled(it) }
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
                SettingsSwitchItem(
                    label = "Relaying",
                    description = "Use relay servers when a direct connection isn't possible",
                    checked = state.relaysEnabled,
                    onCheckedChange = { viewModel.updateRelaysEnabled(it) }
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
                SettingsTextField(
                    label = "Listen address",
                    value = state.guiAddress,
                    onValueChange = { viewModel.updateGuiAddress(it) },
                    supportingText = "Address and port, e.g. 127.0.0.1:8384",
                    modifier = SettingsContentPadding
                )
                SettingsTextField(
                    label = "Username",
                    value = state.guiUser,
                    onValueChange = { viewModel.updateGuiUser(it) },
                    supportingText = "Leave empty to turn off sign-in",
                    modifier = SettingsContentPadding
                )
                SettingsSwitchItem(
                    label = "Use HTTPS",
                    description = "Requires TLS certificates",
                    checked = state.guiUseTLS,
                    onCheckedChange = { viewModel.updateGuiUseTLS(it) }
                )
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

        Spacer(modifier = Modifier.height(16.dp))
    }
}
