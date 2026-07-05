package com.arcadelabs.synapse.features.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.saveSettings() },
                        enabled = !state.isSaving && !state.isLoading
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "Save")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
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
                    SettingsContent(state = state, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    viewModel: SettingsViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- General Section ---
        SettingsSectionHeader(title = "General")

        SettingsCard {
            SettingsTextField(
                label = "Device Name",
                value = state.deviceName,
                onValueChange = { viewModel.updateDeviceName(it) },
                supportingText = "The name by which this device is known to others"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsSwitchRow(
                label = "Usage Reporting",
                description = "Allow anonymous usage data to be sent to the Syncthing developers",
                checked = state.urAccepted > 0,
                onCheckedChange = { viewModel.updateUsageReporting(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            ThemeSelectionRow(
                selectedTheme = state.selectedTheme,
                onThemeSelected = { themeKey ->
                    viewModel.updateSelectedTheme(themeKey)
                    if (themeKey == "TacticalHUD" && state.themeMode == "Light") {
                        viewModel.updateThemeMode("Dark")
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            AppearanceModeRow(
                selectedMode = state.themeMode,
                onModeSelected = { viewModel.updateThemeMode(it) },
                disabledOptions = if (state.selectedTheme == "TacticalHUD") listOf("Light") else emptyList()
            )
        }

        // --- Connections Section ---
        SettingsSectionHeader(title = "Connections")

        SettingsCard {
            SettingsTextField(
                label = "Listen Addresses",
                value = state.listenAddresses,
                onValueChange = { viewModel.updateListenAddresses(it) },
                supportingText = "Comma-separated list of listen addresses (e.g. default, tcp://0.0.0.0:22000)"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsTextField(
                label = "Incoming Rate Limit (kbps)",
                value = state.maxRecvKbps,
                onValueChange = { viewModel.updateMaxRecvKbps(it) },
                keyboardType = KeyboardType.Number,
                supportingText = "0 = unlimited"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsTextField(
                label = "Outgoing Rate Limit (kbps)",
                value = state.maxSendKbps,
                onValueChange = { viewModel.updateMaxSendKbps(it) },
                keyboardType = KeyboardType.Number,
                supportingText = "0 = unlimited"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsSwitchRow(
                label = "NAT Traversal",
                description = "Attempt to punch through NATs",
                checked = state.natEnabled,
                onCheckedChange = { viewModel.updateNatEnabled(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsSwitchRow(
                label = "Local Discovery",
                description = "Discover devices on the local network",
                checked = state.localAnnounceEnabled,
                onCheckedChange = { viewModel.updateLocalAnnounceEnabled(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsSwitchRow(
                label = "Global Discovery",
                description = "Register and look up devices on global discovery servers",
                checked = state.globalAnnounceEnabled,
                onCheckedChange = { viewModel.updateGlobalAnnounceEnabled(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsSwitchRow(
                label = "Relaying",
                description = "Use relay servers when a direct connection cannot be established",
                checked = state.relaysEnabled,
                onCheckedChange = { viewModel.updateRelaysEnabled(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsTextField(
                label = "Global Discovery Servers",
                value = state.globalAnnounceServers,
                onValueChange = { viewModel.updateGlobalAnnounceServers(it) },
                supportingText = "Comma-separated list of discovery server URLs"
            )
        }

        // --- Web GUI Section ---
        SettingsSectionHeader(title = "Web GUI")

        SettingsCard {
            SettingsTextField(
                label = "GUI Listen Address",
                value = state.guiAddress,
                onValueChange = { viewModel.updateGuiAddress(it) },
                supportingText = "Address and port for the web GUI (e.g. 127.0.0.1:8384)"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsTextField(
                label = "GUI Username",
                value = state.guiUser,
                onValueChange = { viewModel.updateGuiUser(it) },
                supportingText = "Username for GUI authentication (leave empty for no auth)"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsSwitchRow(
                label = "Use HTTPS",
                description = "Enable HTTPS for the GUI (requires TLS certificates)",
                checked = state.guiUseTLS,
                onCheckedChange = { viewModel.updateGuiUseTLS(it) }
            )
        }

        // --- Advanced Section ---
        SettingsSectionHeader(title = "Advanced")

        SettingsCard {
            SettingsSwitchRow(
                label = "Crash Reporting",
                description = "Send crash reports to help improve Syncthing",
                checked = state.crashReportingEnabled,
                onCheckedChange = { viewModel.updateCrashReportingEnabled(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// --- Reusable components ---

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        modifier = Modifier.fillMaxWidth(),
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
private fun SettingsSwitchRow(
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
private fun ThemeSelectionRow(
    selectedTheme: String,
    onThemeSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val themes = listOf(
        "Default" to "Standard Purple (Default)",
        "MidnightGreen" to "Midnight Green (Forest)",
        "DeepSpace" to "Deep Space (Sleek Dark)",
        "TacticalHUD" to "Tactical HUD (Cyberpunk)"
    )
    val selectedLabel = themes.find { it.first == selectedTheme }?.second ?: "Standard Purple (Default)"

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

@Composable
private fun AppearanceModeRow(
    selectedMode: String,
    onModeSelected: (String) -> Unit,
    disabledOptions: List<String> = emptyList()
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = "Appearance Mode",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Choose light, dark, or system preference",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        SegmentedButtonRow(
            options = listOf("Light", "Dark", "System"),
            selectedOption = selectedMode,
            onOptionSelected = onModeSelected,
            disabledOptions = disabledOptions,
            modifier = Modifier.width(220.dp)
        )
    }
}

@Composable
private fun SegmentedButtonRow(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    disabledOptions: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(36.dp)
            .border(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(18.dp)
            )
            .background(Color.Transparent)
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selectedOption
            val isDisabled = disabledOptions.contains(option)
            val shape = when (index) {
                0 -> RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
                options.size - 1 -> RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)
                else -> androidx.compose.ui.graphics.RectangleShape
            }
            
            val containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            }
            
            val contentColor = when {
                isDisabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .alpha(if (isDisabled) 0.38f else 1f)
                    .background(containerColor, shape)
                    .clickable(
                        enabled = !isDisabled,
                        onClick = { onOptionSelected(option) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
            
            if (index < options.size - 1) {
                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.fillMaxHeight()
                )
            }
        }
    }
}

