package com.arcadelabs.synapse

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arcadelabs.synapse.core.di.coreDiModule
import com.arcadelabs.synapse.di.appDiModule
import org.koin.core.context.startKoin

fun main() {
    startKoin {
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
        Window(
            onCloseRequest = ::exitApplication,
            title = "Synapse",
        ) {
            App(
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
                    System.exit(0)
                }
            )
        }
    }
}