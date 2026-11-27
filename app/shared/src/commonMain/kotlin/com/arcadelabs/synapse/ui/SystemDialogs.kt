package com.arcadelabs.synapse.ui

import com.arcadelabs.synapse.core.domain.models.formatDeviceId
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.arcadelabs.synapse.core.designsystem.SynapseIcons

/*
 * Material 3 basic dialogs shared by the mobile and desktop shells.
 * Actions are text buttons (dismiss on the left, confirm on the right),
 * the headline uses the AlertDialog default (headlineSmall), and the
 * hero icon follows the M3 "dialog with icon" pattern.
 */

@Composable
fun PairingRequestDialog(
    deviceName: String,
    deviceId: String,
    address: String,
    onAccept: () -> Unit,
    onConfigure: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* stays open until the user chooses */ },
        icon = { Icon(SynapseIcons.Devices, contentDescription = null) },
        title = { Text("Pairing request") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("A device wants to pair with this one.")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    deviceName.ifEmpty { "Unnamed device" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(deviceId.formatDeviceId(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                if (address.isNotEmpty()) {
                    Text(address, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Accept") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
                TextButton(onClick = onConfigure) { Text("Configure") }
            }
        }
    )
}

@Composable
fun FolderOfferDialog(
    folderLabel: String,
    folderId: String,
    offeredByDeviceId: String,
    onConfigure: () -> Unit,
    onDismiss: () -> Unit
) {
    // No direct "Accept": the engine needs a local path to accept an offer,
    // so "Configure" opens the create-folder form where the user picks one.
    AlertDialog(
        onDismissRequest = { /* stays open until the user chooses */ },
        icon = { Icon(SynapseIcons.Folder, contentDescription = null) },
        title = { Text("Folder shared with you") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("A device wants to share a folder with this one.")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    folderLabel.ifEmpty { "Unnamed folder" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text("Folder ID: $folderId", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                Text(
                    "From: ${offeredByDeviceId.formatDeviceId()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfigure) { Text("Configure") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Dismiss") } }
    )
}

/** Confirmation dialog. [destructive] colors the confirm action with the error role. */
@Composable
fun ConfirmDialog(
    icon: ImageVector,
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                }
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
