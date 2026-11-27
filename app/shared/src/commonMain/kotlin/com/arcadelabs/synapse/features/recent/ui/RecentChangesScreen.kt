package com.arcadelabs.synapse.features.recent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcadelabs.synapse.core.designsystem.SynapseIcons
import com.arcadelabs.synapse.core.designsystem.components.EmptyState
import com.arcadelabs.synapse.core.designsystem.components.ErrorState
import com.arcadelabs.synapse.core.designsystem.components.StatusBadge
import com.arcadelabs.synapse.core.designsystem.components.SyncState
import com.arcadelabs.synapse.core.designsystem.components.colors
import com.arcadelabs.synapse.core.domain.state.AsyncResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Recent changes. On mobile it is a full-screen page with a close action;
 * with [embedded] = true (desktop navigation destination) it shows a page
 * headline instead of a top app bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentChangesScreen(
    onCloseClick: () -> Unit,
    embedded: Boolean = false,
    viewModel: RecentChangesViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val isLoading = state.isLoading

    LaunchedEffect(Unit) {
        viewModel.loadRecentChanges()
    }

    if (embedded) {
        com.arcadelabs.synapse.core.designsystem.components.DesktopPage(
            title = "Recent changes",
            actions = {
                IconButton(onClick = { viewModel.loadRecentChanges() }, enabled = !isLoading) {
                    Icon(SynapseIcons.Refresh, contentDescription = "Refresh")
                }
            }
        ) {
            RecentChangesBody(state = state, onRetry = { viewModel.loadRecentChanges() })
        }
        return
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Recent changes") },
                navigationIcon = {
                    IconButton(onClick = onCloseClick) {
                        Icon(SynapseIcons.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadRecentChanges() }, enabled = !isLoading) {
                        Icon(SynapseIcons.Refresh, contentDescription = "Refresh")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            RecentChangesBody(state = state, onRetry = { viewModel.loadRecentChanges() })
        }
    }
}

@Composable
private fun RecentChangesBody(
    state: RecentChangesState,
    onRetry: () -> Unit
) {
    val changes = state.changes
    Box(modifier = Modifier.fillMaxSize()) {
        val res = state.resource
        when {
            res is AsyncResource.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            res is AsyncResource.Error && changes.isEmpty() -> ErrorState(
                message = res.message.ifEmpty { state.error ?: "Couldn't load recent changes" },
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.Center)
            )
            changes.isEmpty() -> EmptyState(
                icon = SynapseIcons.History,
                title = "No recent changes",
                body = "Files that sync will show up here.",
                modifier = Modifier.align(Alignment.Center)
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(changes, key = { it.id }) { change -> RecentChangeItemCard(change = change) }
            }
        }
    }
}

/** One change: M3 list item with a colored action badge. */
@Composable
fun RecentChangeItemCard(change: RecentChangeUiModel) {
    val actionText = change.action.replaceFirstChar { it.uppercase() }
    val actionColors = when (change.action.lowercase()) {
        "update" -> SyncState.Connected.colors()
        "delete" -> SyncState.Disconnected.colors()
        else -> SyncState.Paused.colors()
    }

    ListItem(
        headlineContent = {
            Text(change.itemName, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                Text("${change.folderId} · ${change.time}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (change.error != null) {
                    Text(
                        text = "Error: ${change.error}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        trailingContent = {
            StatusBadge(
                text = actionText,
                containerColor = actionColors.container,
                contentColor = actionColors.onContainer
            )
        }
    )
}
