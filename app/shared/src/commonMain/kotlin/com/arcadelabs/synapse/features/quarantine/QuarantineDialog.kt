package com.arcadelabs.synapse.features.quarantine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
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
            shape = SynapseDialogShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
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
                                text = "Virtual Quarantine",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (uiState.conflicts.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "${uiState.conflicts.size} Conflicts",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Conflicting concurrent edits held safely with zero disk pollution",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { viewModel.loadConflicts() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh conflicts")
                        }
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Feedback Banner
                if (uiState.error != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
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
                        color = SynapseStatusColors.Connected.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = uiState.successMessage ?: "",
                            color = SynapseStatusColors.Connected,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
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
                                shape = RoundedCornerShape(50),
                                color = SynapseStatusColors.Connected.copy(alpha = 0.15f),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = SynapseStatusColors.Connected,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Quarantine is Empty",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "All synchronizing files are clean and converged across your cluster.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                                onIgnore = { viewModel.ignore(conflict.conflictId) },
                                onReopen = { viewModel.reopen(conflict.conflictId) }
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
                    Button(
                        shape = SynapsePillShape,
                        onClick = onDismissRequest
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictItemCard(
    conflict: CoreQuarantineConflict,
    isSelected: Boolean,
    isDiffLoading: Boolean,
    diffResponse: CoreDiffResponse?,
    onInspectDiffClick: () -> Unit,
    onResolve: (CoreResolveAction) -> Unit,
    onIgnore: () -> Unit,
    onReopen: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
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
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Folder: ${conflict.folderId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    shape = SynapsePillShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    onClick = { onResolve(CoreResolveAction.ACCEPT_LOCAL) }
                ) {
                    Text("Keep Local", style = MaterialTheme.typography.labelMedium)
                }

                FilledTonalButton(
                    shape = SynapsePillShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    onClick = { onResolve(CoreResolveAction.ACCEPT_REMOTE) }
                ) {
                    Text("Keep Remote", style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    shape = SynapsePillShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    onClick = { onResolve(CoreResolveAction.KEEP_BOTH) }
                ) {
                    Text("Keep Both", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    shape = SynapsePillShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    onClick = onInspectDiffClick
                ) {
                    Text(if (isSelected) "Hide Diff" else "Inspect Diff", style = MaterialTheme.typography.labelMedium)
                }

                if (conflict.status != "IGNORED") {
                    TextButton(
                        shape = SynapsePillShape,
                        onClick = onIgnore
                    ) {
                        Text("Ignore", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    TextButton(
                        shape = SynapsePillShape,
                        onClick = onReopen
                    ) {
                        Text("Reopen", color = MaterialTheme.colorScheme.primary)
                    }
                }
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
        "RESOLVED" -> SynapseStatusColors.Connected.copy(alpha = 0.2f) to SynapseStatusColors.Connected
        "IGNORED" -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = status,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun DiffViewer(diffResponse: CoreDiffResponse) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
                .heightIn(max = 260.dp)
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
            Color(0xFF2E7D32).copy(alpha = 0.15f),
            "+ [Local]  ",
            hunk.localLines
        )
        CoreHunkType.REMOTE_CHANGE -> Triple(
            Color(0xFF0277BD).copy(alpha = 0.15f),
            "+ [Remote] ",
            hunk.remoteLines
        )
        CoreHunkType.CONFLICT -> Triple(
            Color(0xFFC62828).copy(alpha = 0.2f),
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
            .background(bg, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        textLines.forEach { line ->
            Text(
                text = "$prefix$line",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
