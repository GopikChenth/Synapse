package com.arcadelabs.synapse

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.arcadelabs.synapse.core.di.coreDiModule
import org.koin.core.context.startKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin {
        modules(
            coreDiModule,
            org.koin.dsl.module {
                single<com.arcadelabs.synapse.core.network.ApiKeyProvider> { com.arcadelabs.synapse.core.network.EmptyApiKeyProvider() }
            }
        )
    }

    ComposeViewport {
        App()
    }
}