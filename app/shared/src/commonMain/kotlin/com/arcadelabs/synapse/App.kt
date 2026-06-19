package com.arcadelabs.synapse

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.arcadelabs.synapse.features.devices.ui.DevicesScreen
import com.arcadelabs.synapse.features.folders.ui.FoldersScreen
import com.arcadelabs.synapse.features.status.ui.StatusScreen

enum class Screen(val title: String) {
    STATUS("Status"),
    FOLDERS("Folders"),
    DEVICES("Devices")
}

@Composable
fun App() {
    var currentScreen by remember { mutableStateOf(Screen.STATUS) }

    MaterialTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Screen.entries.forEach { screen ->
                        val selected = currentScreen == screen
                        val icon = when (screen) {
                            Screen.STATUS -> Icons.Default.Info
                            Screen.FOLDERS -> Icons.Default.Folder
                            Screen.DEVICES -> Icons.Default.Share
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentScreen = screen },
                            icon = { Icon(icon, contentDescription = screen.title) },
                            label = { Text(screen.title) }
                        )
                    }
                }
            }
        ) { paddingValues ->
            AnimatedContent(
                targetState = currentScreen,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                transitionSpec = {
                    val initialIndex = initialState.ordinal
                    val targetIndex = targetState.ordinal
                    if (targetIndex > initialIndex) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut()
                        )
                    }
                }
            ) { screen ->
                when (screen) {
                    Screen.STATUS -> StatusScreen()
                    Screen.FOLDERS -> FoldersScreen()
                    Screen.DEVICES -> DevicesScreen()
                }
            }
        }
    }
}