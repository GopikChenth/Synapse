package com.arcadelabs.synapse.core.designsystem.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arcadelabs.synapse.core.designsystem.LocalDynamicColorAvailable
import com.arcadelabs.synapse.core.designsystem.SynapseIcons
import com.arcadelabs.synapse.core.designsystem.SynapseThemes

/*
 * Material 3 settings building blocks shared by the mobile and desktop
 * settings screens. Rows are M3 ListItems so they get the standard
 * 56/72dp heights, state layers (ripple), and accessibility semantics.
 */

/** M3 list subheader: titleSmall in primary. */
@Composable
fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
    )
}

/** A filled M3 card grouping related settings. */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = modifier.fillMaxWidth().animateContentSize()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            if (title != null) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            content()
        }
    }
}

/** Padding that lines non-ListItem content (fields, buttons) up with ListItem text. */
val SettingsContentPadding = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)

@Composable
fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

/** Whole row toggles the switch (M3 list item with trailing switch). */
@Composable
fun SettingsSwitchItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = description?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent), // sits on the group's card
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange
        )
    )
}

/** Theme picker: list item that opens an M3 menu. Offers dynamic color where supported. */
@Composable
fun SettingsThemeItem(
    selectedTheme: String,
    onThemeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val themes = buildList {
        if (LocalDynamicColorAvailable.current) add(SynapseThemes.Dynamic to "Dynamic color (wallpaper)")
        add(SynapseThemes.Default to "Synapse Purple")
        add(SynapseThemes.MidnightGreen to "Midnight Green")
        add(SynapseThemes.DeepSpace to "Deep Space")
        add(SynapseThemes.TacticalHUD to "Tactical HUD")
    }
    val selectedLabel = themes.find { it.first == selectedTheme }?.second ?: "Synapse Purple"

    Box(modifier = modifier) {
        ListItem(
            headlineContent = { Text("Theme") },
            supportingContent = { Text(selectedLabel) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent), // sits on the group's card
            modifier = Modifier.clickable(role = Role.DropdownList) { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            themes.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = if (key == selectedTheme) {
                        { Icon(SynapseIcons.Check, contentDescription = null) }
                    } else null,
                    onClick = {
                        onThemeSelected(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Light / Dark / System as an M3 single-choice segmented button row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsThemeModeItem(
    selectedMode: String,
    onModeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf("Light", "Dark", "System")
    Column(modifier = modifier.then(SettingsContentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Appearance", style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                val icon = when (option) {
                    "Light" -> SynapseIcons.Sun
                    "Dark" -> SynapseIcons.Moon
                    else -> SynapseIcons.System
                }
                SegmentedButton(
                    selected = option == selectedMode,
                    onClick = { onModeSelected(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = option == selectedMode) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                        }
                    }
                ) {
                    Text(option)
                }
            }
        }
    }
}
