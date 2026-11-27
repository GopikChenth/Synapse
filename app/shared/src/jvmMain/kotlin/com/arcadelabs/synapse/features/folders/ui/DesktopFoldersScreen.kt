package com.arcadelabs.synapse.features.folders.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.arcadelabs.synapse.core.designsystem.SynapseIcons
import com.arcadelabs.synapse.core.designsystem.components.DesktopPage
import com.arcadelabs.synapse.core.domain.models.normalizeDeviceId
import com.arcadelabs.synapse.features.status.ui.StatusViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Desktop folders page: the shared M3 folder list in a desktop page layout. */
@Composable
fun DesktopFoldersScreen(
    onAddFolderClick: () -> Unit,
    onEditFolderClick: (com.arcadelabs.synapse.core.domain.models.Folder) -> Unit = {},
    openFolder: ((String) -> Unit)? = null,
    viewModel: FolderViewModel = koinViewModel()
) {
    DesktopPage(
        title = "Folders",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddFolderClick,
                icon = { Icon(SynapseIcons.Add, contentDescription = null) },
                text = { Text("Add folder") }
            )
        }
    ) {
        FoldersScreen(
            onAddFolderClick = onAddFolderClick,
            onEditFolderClick = onEditFolderClick,
            openFolder = openFolder,
            viewModel = viewModel
        )
    }
}

/** Desktop "Add folder" — an M3 basic dialog around the shared folder form. */
@Composable
fun DesktopCreateFolderDialog(
    onDismiss: () -> Unit,
    selectDirectory: ((onPathSelected: (String) -> Unit) -> Unit)? = null,
    prefilledFolderId: String = "",
    @Suppress("UNUSED_PARAMETER") prefilledFolderLabel: String = "", // engine ignores labels; kept for DesktopApp.kt callers
    prefilledSharedDevices: List<String> = emptyList(),
    viewModel: FolderViewModel = koinViewModel(),
    statusViewModel: StatusViewModel = koinViewModel()
) {
    val allDevices by viewModel.devicesState.collectAsState()
    val statusState by statusViewModel.statusState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val form = rememberNewFolderFormState(prefilledFolderId, prefilledSharedDevices)

    // Only remote devices — never this device itself.
    val myId = statusState.myId
    val remoteDevices = allDevices
        .filter { it.deviceID.normalizeDeviceId() != myId.normalizeDeviceId() }
        .map { it.deviceID to it.name }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(SynapseIcons.Folder, contentDescription = null) },
        title = { Text("Add folder") },
        text = {
            NewFolderFormFields(
                form = form,
                devices = remoteDevices,
                error = error,
                selectDirectory = selectDirectory,
                pathPlaceholder = "e.g. C:\\Users\\you\\Documents",
                firstFieldModifier = Modifier.focusRequester(focusRequester),
                modifier = Modifier
                    .widthIn(min = 400.dp, max = 560.dp)
                    .verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.submitNewFolder(form, onSuccess = onDismiss) },
                enabled = form.canSave && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
