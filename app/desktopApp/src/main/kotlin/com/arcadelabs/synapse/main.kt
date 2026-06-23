package com.arcadelabs.synapse

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arcadelabs.synapse.core.di.coreDiModule
import com.arcadelabs.synapse.daemon.DaemonState
import com.arcadelabs.synapse.daemon.DaemonStartupScreen
import com.arcadelabs.synapse.daemon.DesktopDaemonManager
import com.arcadelabs.synapse.di.appDiModule
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        allowOverride(true)
        modules(
            coreDiModule, 
            appDiModule,
            org.koin.dsl.module {
                single<com.arcadelabs.synapse.core.network.ApiKeyProvider> {
                    com.arcadelabs.synapse.core.network.JvmApiKeyProvider(get())
                }
            }
        )
    }
    application {
        val daemonManager = DesktopDaemonManager()

        Window(
            onCloseRequest = {
                daemonManager.stop()
                exitApplication()
            },
            title = "Synapse",
        ) {
            val daemonState by daemonManager.state.collectAsState()
            var isReady by remember { mutableStateOf(false) }
            val preferencesHelper: com.arcadelabs.synapse.core.prefs.PreferencesHelper = org.koin.compose.koinInject()

            LaunchedEffect(Unit) {
                daemonManager.start()
            }

            LaunchedEffect(daemonState) {
                if (daemonState is DaemonState.Ready) {
                    val readyState = daemonState as DaemonState.Ready
                    preferencesHelper.apiBaseUrl = readyState.apiBaseUrl
                    preferencesHelper.apiKey = readyState.apiKey
                    isReady = true
                }
            }

            MaterialTheme {
                if (isReady) {
                    DesktopApp(
                        openFolder = { path ->
                            try {
                                val file = java.io.File(path)
                                if (file.exists() && java.awt.Desktop.isDesktopSupported()) {
                                    java.awt.Desktop.getDesktop().open(file)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        selectDirectory = { onPathSelected ->
                            try {
                                val chooser = javax.swing.JFileChooser().apply {
                                    fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                                    dialogTitle = "Select Directory"
                                }
                                val result = chooser.showOpenDialog(null)
                                if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                                    onPathSelected(chooser.selectedFile.absolutePath)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        openUrl = { url ->
                            try {
                                if (java.awt.Desktop.isDesktopSupported()) {
                                    java.awt.Desktop.getDesktop().browse(java.net.URI(url))
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        exitApp = {
                            daemonManager.stop()
                            System.exit(0)
                        }
                    )
                } else {
                    DaemonStartupScreen(
                        state = daemonState,
                        onRetry = {
                            daemonManager.start()
                        }
                    )
                }
            }
        }
    }
}