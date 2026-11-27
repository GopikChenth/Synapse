package com.arcadelabs.synapse.features.quarantine

import androidx.compose.foundation.background
import com.arcadelabs.synapse.core.designsystem.SynapseIcons
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.arcadelabs.synapse.core.designsystem.SynapseDialogShape
import com.arcadelabs.synapse.core.designsystem.SynapsePillShape
import com.arcadelabs.synapse.core.designsystem.SynapseStatusColors
import com.arcadelabs.synapse.core.domain.models.CoreDiffHunk
import com.arcadelabs.synapse.core.domain.models.CoreDiffResponse
import com.arcadelabs.synapse.core.domain.models.CoreHunkType
import com.arcadelabs.synapse.core.domain.models.CoreQuarantineConflict
import com.arcadelabs.synapse.core.domain.models.CoreResolveAction
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuarantineDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuarantineViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .widthIn(max = 840.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Conflicts",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            if (uiState.conflicts.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                com.arcadelabs.synapse.core.designsystem.components.StatusBadge(
                                    text = "${uiState.conflicts.size} conflicts",
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        Text(
                            text = "Edits made on two devices at once are held here until you choose which to keep.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { viewModel.loadConflicts() }) {
                            Icon(SynapseIcons.Refresh, contentDescription = "Refresh conflicts")
                        }
                        IconButton(onClick = onDismissRequest) {
                            Icon(SynapseIcons.Close, contentDescription = "Close")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Feedback Banner
                if (uiState.error != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = uiState.error ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (uiState.successMessage != null) {
                    Surface(
                        color = SynapseStatusColors.ConnectedContainer,
                        contentColor = SynapseStatusColors.OnConnectedContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = uiState.successMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Main Content
                if (uiState.isLoading && uiState.conflicts.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.conflicts.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = SynapseStatusColors.ConnectedContainer,
                                contentColor = SynapseStatusColors.OnConnectedContainer,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(SynapseIcons.CheckCircle, contentDescription = null, modifier = Modifier.size(36.dp))
                                }
                            }
                            Text(
                                text = "No conflicts",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Every synced file matches across your devices.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.conflicts, key = { it.conflictId }) { conflict ->
                            ConflictItemCard(
                                conflict = conflict,
                                isSelected = uiState.selectedConflictId == conflict.conflictId,
                                isDiffLoading = uiState.isDiffLoading && uiState.selectedConflictId == conflict.conflictId,
                                diffResponse = if (uiState.selectedConflictId == conflict.conflictId) uiState.selectedDiff else null,
                                onInspectDiffClick = { viewModel.inspectDiff(conflict.conflictId) },
                                onResolve = { action -> viewModel.resolve(conflict.conflictId, action) },
                                onIgnore = { viewModel.ignore(conflict.conflictId) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConflictItemCard(
    conflict: CoreQuarantineConflict,
    isSelected: Boolean,
    isDiffLoading: Boolean,
    diffResponse: CoreDiffResponse?,
    onInspectDiffClick: () -> Unit,
    onResolve: (CoreResolveAction) -> Unit,
    onIgnore: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = conflict.relativePath,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Folder: ${conflict.folderId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StatusPill(status = conflict.status)
            }

            if (conflict.stateDescription.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = conflict.stateDescription,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions wrap onto more lines on narrow windows.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { onResolve(CoreResolveAction.KEEP_BOTH) }) {
                    Text("Keep both")
                }
                FilledTonalButton(onClick = { onResolve(CoreResolveAction.ACCEPT_LOCAL) }) {
                    Text("Keep this device's")
                }
                FilledTonalButton(onClick = { onResolve(CoreResolveAction.ACCEPT_REMOTE) }) {
                    Text("Keep other device's")
                }
                OutlinedButton(onClick = onInspectDiffClick) {
                    Text(if (isSelected) "Hide changes" else "Compare")
                }
                // The engine lists only pending conflicts, so every card can be ignored.
                TextButton(onClick = onIgnore) { Text("Ignore") }
            }

            // Expanded Diff View
            if (isSelected) {
                Spacer(modifier = Modifier.height(12.dp))
                if (isDiffLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else if (diffResponse != null) {
                    DiffViewer(diffResponse = diffResponse)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val (bg, fg) = when (status.uppercase()) {
        "PENDING" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        "RESOLVED" -> SynapseStatusColors.ConnectedContainer to SynapseStatusColors.OnConnectedContainer
        "IGNORED" -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }

    com.arcadelabs.synapse.core.designsystem.components.StatusBadge(
        text = status.lowercase().replaceFirstChar { it.uppercase() },
        containerColor = bg,
        contentColor = fg
    )
}

@Composable
private fun DiffViewer(diffResponse: CoreDiffResponse) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                // Cap the height before scrolling: this sits inside a LazyColumn item, which
                // measures children with an unbounded height and crashes a bare verticalScroll.
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = "3-Way Merge Inspection: ${diffResponse.relativePath}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (diffResponse.hunks.isEmpty()) {
                Text(
                    text = "No granular hunks available. File may be binary or single-block payload.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                diffResponse.hunks.forEach { hunk ->
                    DiffHunkRow(hunk = hunk)
                }
            }
        }
    }
}

@Composable
private fun DiffHunkRow(hunk: CoreDiffHunk) {
    val (bg, prefix, textLines) = when (hunk.hunkType) {
        CoreHunkType.LOCAL_CHANGE -> Triple(
            SynapseStatusColors.ConnectedContainer,
            "+ [Local]  ",
            hunk.localLines
        )
        CoreHunkType.REMOTE_CHANGE -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            "+ [Remote] ",
            hunk.remoteLines
        )
        CoreHunkType.CONFLICT -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            "! [COLLIDE] ",
            (hunk.localLines.map { "Local: $it" } + hunk.remoteLines.map { "Remote: $it" })
        )
        CoreHunkType.UNCHANGED, CoreHunkType.IDENTICAL_CHANGE -> Triple(
            Color.Transparent,
            "  ",
            hunk.baseLines
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, shape = MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        textLines.forEach { line ->
            Text(
                text = "$prefix$line",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = contentColorFor(bg).takeIf { bg != Color.Transparent } ?: MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
