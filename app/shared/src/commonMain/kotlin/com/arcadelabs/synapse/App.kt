package com.arcadelabs.synapse

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.arcadelabs.synapse.core.designsystem.DevicesIcon
import com.arcadelabs.synapse.core.designsystem.FolderIcon
import com.arcadelabs.synapse.features.devices.ui.DevicesScreen
import com.arcadelabs.synapse.features.folders.ui.CreateFolderDialog
import com.arcadelabs.synapse.features.folders.ui.FoldersScreen
import com.arcadelabs.synapse.features.status.ui.StatusScreen
import kotlinx.coroutines.launch

enum class Screen(val title: String) {
    FOLDERS("Folders"),
    DEVICES("Devices"),
    STATUS("Status")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(
    openFolder: ((String) -> Unit)? = null,
    selectDirectory: ((onPathSelected: (String) -> Unit) -> Unit)? = null
) {
    var isCreateFolderDialogOpen by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(
        initialPage = Screen.FOLDERS.ordinal,
        pageCount = { Screen.entries.size }
    )
    val coroutineScope = rememberCoroutineScope()

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Synapse") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                },
                bottomBar = {
                    NavigationBar {
                        Screen.entries.forEach { screen ->
                            val selected = pagerState.currentPage == screen.ordinal
                            val icon = when (screen) {
                                Screen.FOLDERS -> FolderIcon
                                Screen.DEVICES -> DevicesIcon
                                Screen.STATUS -> Icons.Default.Info
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(screen.ordinal)
                                    }
                                },
                                icon = { Icon(icon, contentDescription = screen.title) },
                                label = { Text(screen.title) }
                            )
                        }
                    }
                }
            ) { paddingValues ->
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) { pageIndex ->
                    val screen = Screen.entries[pageIndex]
                    when (screen) {
                        Screen.FOLDERS -> FoldersScreen(
                            onAddFolderClick = { isCreateFolderDialogOpen = true },
                            openFolder = openFolder
                        )
                        Screen.DEVICES -> DevicesScreen()
                        Screen.STATUS -> StatusScreen()
                    }
                }
            }

            // Root level Full-screen Dialog Overlay
            AnimatedVisibility(
                visible = isCreateFolderDialogOpen,
                enter = scaleIn(initialScale = 0.8f) + fadeIn(),
                exit = scaleOut(targetScale = 0.8f) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    CreateFolderDialog(
                        onDismiss = { isCreateFolderDialogOpen = false },
                        selectDirectory = selectDirectory
                    )
                }
            }
        }
    }
}