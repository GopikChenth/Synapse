package com.arcadelabs.synapse.features.folders.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcadelabs.synapse.SynapseBackHandler
import com.arcadelabs.synapse.core.designsystem.SynapseIcons
import com.arcadelabs.synapse.core.designsystem.components.EmptyState
import com.arcadelabs.synapse.core.designsystem.components.ErrorState
import com.arcadelabs.synapse.core.designsystem.components.LeadingIcon
import com.arcadelabs.synapse.core.designsystem.components.SyncState
import com.arcadelabs.synapse.core.designsystem.components.SyncStateBadge
import com.arcadelabs.synapse.core.domain.models.normalizeDeviceId
import com.arcadelabs.synapse.core.domain.state.AsyncResource
import com.arcadelabs.synapse.ui.ConfirmDialog
import org.koin.compose.viewmodel.koinViewModel

fun generateRandomFolderId(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    val part1 = (1..5).map { chars.random() }.joinToString("")
    val part2 = (1..5).map { chars.random() }.joinToString("")
    return "$part1-$part2"
}

// ─────────────────────────────────────────────────────────────────────────────
// Folder list (shared by mobile and desktop)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FoldersScreen(
    onAddFolderClick: () -> Unit,
    onEditFolderClick: (com.arcadelabs.synapse.core.domain.models.Folder) -> Unit = {},
    onAddDeviceClick: (String, String) -> Unit = { _, _ -> },
    openFolder: ((String) -> Unit)? = null,
    viewModel: FolderViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val folders = state.folders
    val error = state.error

    Box(modifier = Modifier.fillMaxSize()) {
        val res = state.resource
        when {
            res is AsyncResource.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            res is AsyncResource.Error && folders.isEmpty() -> {
                ErrorState(
                    message = res.message.ifEmpty { error ?: "Couldn't load folders" },
                    onRetry = { viewModel.loadFolders() },
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            folders.isEmpty() -> {
                EmptyState(
                    icon = SynapseIcons.Folder,
                    title = "No folders yet",
                    body = "Add a folder to start syncing it with your devices.",
                    modifier = Modifier.align(Alignment.Center),
                    action = {
                        FilledTonalButton(onClick = onAddFolderClick) { Text("Add folder") }
                    }
                )
            }
            else -> {
                LazyColumn(
                    // Bottom padding keeps the last row clear of the extended FAB.
                    contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(folders, key = { it.id }) { folder ->
                        FolderCard(
                            folder = folder,
                            onOpenClick = { onEditFolderClick(folder) },
                            onDeleteFolder = { viewModel.deleteFolder(it) },
                            onRescanFolder = { viewModel.rescanFolder(it) },
                            onPauseToggle = { viewModel.pauseFolder(folder.id, !folder.paused) },
                            onOpenInFiles = openFolder?.let { open -> { open(folder.path) } }
                        )
                    }
                }
            }
        }
    }
}

/** Folder row: M3 two-line list item with leading icon, state badge and overflow menu. */
@Composable
fun FolderCard(
    folder: com.arcadelabs.synapse.core.domain.models.Folder,
    onOpenClick: () -> Unit,
    onDeleteFolder: (String) -> Unit = {},
    onRescanFolder: (String) -> Unit = {},
    onPauseToggle: (() -> Unit)? = null,
    onOpenInFiles: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        ConfirmDialog(
            icon = SynapseIcons.Delete,
            title = "Remove \"${folder.label.ifEmpty { folder.id }}\"?",
            text = "The folder stops syncing. Files on this device are not deleted.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                onDeleteFolder(folder.id)
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    ListItem(
        modifier = Modifier.clickable(onClick = onOpenClick),
        leadingContent = { LeadingIcon(SynapseIcons.Folder) },
        headlineContent = {
            Text(folder.label.ifEmpty { folder.id }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(folder.path, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SyncStateBadge(
                    state = if (folder.paused) SyncState.Paused else SyncState.Connected,
                    label = if (folder.paused) "Paused" else "Synced"
                )
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(SynapseIcons.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rescan") },
                            leadingIcon = { Icon(SynapseIcons.Refresh, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRescanFolder(folder.id)
                            }
                        )
                        if (onPauseToggle != null) {
                            DropdownMenuItem(
                                text = { Text(if (folder.paused) "Resume" else "Pause") },
                                leadingIcon = {
                                    Icon(if (folder.paused) SynapseIcons.PlayArrow else SynapseIcons.Pause, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onPauseToggle()
                                }
                            )
                        }
                        if (onOpenInFiles != null) {
                            DropdownMenuItem(
                                text = { Text("Open folder") },
                                leadingIcon = { Icon(SynapseIcons.Folder, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onOpenInFiles()
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            leadingIcon = { Icon(SynapseIcons.Delete, contentDescription = null) },
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.error,
                                leadingIconColor = MaterialTheme.colorScheme.error
                            ),
                            onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// New-folder form (shared by the mobile full-screen dialog and desktop dialog)
// ─────────────────────────────────────────────────────────────────────────────

internal val FolderTypeOptions = listOf(
    "sendreceive" to "Send & receive",
    "sendonly" to "Send only",
    "receiveonly" to "Receive only"
)

internal val VersioningOptions = listOf(
    "none" to "No versioning",
    "trashcan" to "Trash can",
    "simple" to "Simple",
    "staggered" to "Staggered"
)

@Stable
class NewFolderFormState(
    prefilledId: String,
    prefilledSharedDevices: List<String>
) {
    var id by mutableStateOf(prefilledId.ifEmpty { generateRandomFolderId() })
    var path by mutableStateOf("")
    val selectedDevices = mutableStateListOf<String>().apply { addAll(prefilledSharedDevices.map { it.normalizeDeviceId() }) }
    var type by mutableStateOf("sendreceive")
    var paused by mutableStateOf(false)
    var versioningType by mutableStateOf("none")

    val canSave: Boolean get() = id.isNotBlank() && path.isNotBlank()

    fun toggleDevice(deviceId: String) {
        val normalized = deviceId.normalizeDeviceId()
        if (selectedDevices.contains(normalized)) selectedDevices.remove(normalized) else selectedDevices.add(normalized)
    }
}

@Composable
fun rememberNewFolderFormState(
    prefilledId: String = "",
    prefilledSharedDevices: List<String> = emptyList()
): NewFolderFormState = remember { NewFolderFormState(prefilledId, prefilledSharedDevices) }

fun FolderViewModel.submitNewFolder(form: NewFolderFormState, onSuccess: () -> Unit) {
    if (!form.canSave) return
    createFolder(
        id = form.id.trim(),
        path = form.path.trim(),
        type = form.type,
        paused = form.paused,
        sharedDevices = form.selectedDevices.toList(),
        versioningType = form.versioningType,
        onSuccess = onSuccess
    )
}

/**
 * The new-folder fields. [devices] is a list of (deviceId, displayName).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewFolderFormFields(
    form: NewFolderFormState,
    devices: List<Pair<String, String>>,
    error: String?,
    selectDirectory: ((onPathSelected: (String) -> Unit) -> Unit)?,
    pathPlaceholder: String,
    modifier: Modifier = Modifier,
    firstFieldModifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(SynapseIcons.Error, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        OutlinedTextField(
            value = form.id,
            onValueChange = { form.id = it },
            label = { Text("Folder ID") },
            singleLine = true,
            supportingText = { Text("Must match the ID on the other devices") },
            modifier = firstFieldModifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form.path,
            onValueChange = { form.path = it },
            label = { Text("Folder path") },
            singleLine = true,
            trailingIcon = if (selectDirectory != null) {
                {
                    IconButton(onClick = { selectDirectory { form.path = it } }) {
                        Icon(SynapseIcons.Folder, contentDescription = "Browse")
                    }
                }
            } else null,
            placeholder = { Text(pathPlaceholder) },
            modifier = Modifier.fillMaxWidth()
        )

        OptionDropdownField(
            label = "Folder type",
            options = FolderTypeOptions,
            selected = form.type,
            onSelected = { form.type = it }
        )

        if (devices.isNotEmpty()) {
            Text(
                text = "Share with",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
            devices.forEach { (deviceId, name) ->
                FormCheckboxRow(
                    label = name.ifEmpty { deviceId.take(8) + "…" },
                    checked = form.selectedDevices.contains(deviceId.normalizeDeviceId()),
                    onCheckedChange = { form.toggleDevice(deviceId) },
                    icon = SynapseIcons.Devices
                )
            }
        }

        FormSwitchRow(
            label = "Start paused",
            checked = form.paused,
            onCheckedChange = { form.paused = it }
        )

        OptionDropdownField(
            label = "File versioning",
            options = VersioningOptions,
            selected = form.versioningType,
            onSelected = { form.versioningType = it }
        )
    }
}

/** M3 exposed dropdown (read-only text field + menu). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionDropdownField(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: options.first().second
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

/** Form row whose whole surface toggles a switch (M3 switch + state layer). */
@Composable
fun FormSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Form row with a leading checkbox — M3 multi-select. */
@Composable
fun FormCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(horizontal = 12.dp))
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mobile: full-screen "Add folder" dialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFolderSheet(
    onDismiss: () -> Unit,
    selectDirectory: ((onPathSelected: (String) -> Unit) -> Unit)? = null,
    prefilledFolderId: String = "",
    @Suppress("UNUSED_PARAMETER") prefilledFolderLabel: String = "", // engine ignores labels; kept for App.kt callers
    prefilledSharedDevices: List<String> = emptyList(),
    viewModel: FolderViewModel = koinViewModel()
) {
    val devices by viewModel.devicesState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val form = rememberNewFolderFormState(prefilledFolderId, prefilledSharedDevices)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    SynapseBackHandler(enabled = true, onBack = onDismiss)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Add folder") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(SynapseIcons.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.padding(horizontal = 16.dp).size(24.dp))
                    } else {
                        TextButton(
                            onClick = { viewModel.submitNewFolder(form, onSuccess = onDismiss) },
                            enabled = form.canSave
                        ) {
                            Text("Save")
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        NewFolderFormFields(
            form = form,
            devices = devices.map { it.deviceID to it.name },
            error = error,
            selectDirectory = selectDirectory,
            pathPlaceholder = "/storage/emulated/0/Documents",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 16.dp)
        )
    }
}

// Kept for App.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    selectDirectory: ((onPathSelected: (String) -> Unit) -> Unit)? = null,
    prefilledFolderId: String = "",
    prefilledFolderLabel: String = "",
    prefilledSharedDevices: List<String> = emptyList(),
    viewModel: FolderViewModel = koinViewModel()
) = CreateFolderSheet(
    onDismiss = onDismiss,
    selectDirectory = selectDirectory,
    prefilledFolderId = prefilledFolderId,
    prefilledFolderLabel = prefilledFolderLabel,
    prefilledSharedDevices = prefilledSharedDevices,
    viewModel = viewModel
)

/** Icon + content row used by the device forms. */
@Composable
fun FormField(
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
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = if (verticalAlignment == Alignment.Top) 2.dp else 0.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}
