package com.arcadelabs.synapse

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arcadelabs.synapse.core.di.coreDiModule
import com.arcadelabs.synapse.di.appDiModule
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(coreDiModule, appDiModule)
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
                }
            )
        }
    }
}