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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
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
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        shape = RoundedCornerShape(12.dp),
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
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
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
            onCheckedChange = null // Row handles clicking to prevent double toggles
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
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { expanded = true }
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "App Theme",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Box {
            Text(
                text = selectedLabel,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 8.dp)
            )
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
        Text(
            text = "Appearance Mode",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        
        SegmentedButtonRow(
            options = listOf("Light", "Dark", "System"),
            selectedOption = selectedMode,
            onOptionSelected = onModeSelected,
            disabledOptions = disabledOptions,
            modifier = Modifier.width(240.dp)
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
            .fillMaxWidth()
            .height(40.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val isSelected = option == selectedOption
            val isDisabled = disabledOptions.contains(option)
            val interactionSource = remember { MutableInteractionSource() }
            
            // Layout Weight Morphing: Selected is 1.6f, Unselected is 0.7f
            val weight by animateFloatAsState(
                targetValue = if (isSelected) 1.6f else 0.7f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            
            // Shape Morphing: Active morphs to 20.dp (fully rounded), Inactive morphs to 6.dp
            val cornerRadius by animateDpAsState(
                targetValue = if (isSelected) 20.dp else 6.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            
            val containerColor by animateColorAsState(
                targetValue = when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            
            val contentColor by animateColorAsState(
                targetValue = when {
                    isDisabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = tween(durationMillis = 200)
            )

            val icon = when (option) {
                "Light" -> com.arcadelabs.synapse.core.designsystem.SunIcon
                "Dark" -> com.arcadelabs.synapse.core.designsystem.MoonIcon
                else -> com.arcadelabs.synapse.core.designsystem.SystemIcon
            }

            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .background(
                        color = containerColor,
                        shape = RoundedCornerShape(cornerRadius.coerceAtLeast(0.dp))
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = !isDisabled,
                        onClick = { onOptionSelected(option) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = option,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSelected,
                        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + 
                                expandHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
                        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + 
                               shrinkHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                    ) {
                        Row {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = option,
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.bounceClick(interactionSource: MutableInteractionSource): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

