package com.arcadelabs.synapse

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arcadelabs.synapse.core.di.coreDiModule
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(coreDiModule)
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Synapse",
        ) {
            App()
        }
    }
}