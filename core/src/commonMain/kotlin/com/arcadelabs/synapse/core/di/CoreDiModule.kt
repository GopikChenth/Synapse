package com.arcadelabs.synapse.core.di

import com.arcadelabs.synapse.core.network.createHttpClient
import com.arcadelabs.synapse.core.network.SynapseApiClient
import com.arcadelabs.synapse.core.network.SynapseApiClientImpl
import com.arcadelabs.synapse.core.network.SynapseCoreClient
import com.arcadelabs.synapse.core.network.SynapseCoreClientImpl
import com.arcadelabs.synapse.core.prefs.PreferencesHelper
import com.arcadelabs.synapse.core.prefs.PreferencesHelperImpl
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

/**
 * Dependency injection module for the core module.
 * Provides the HTTP client, platform preferences helper, the Synapse API client,
 * and the native Synapse Core client.
 */
val coreDiModule = module {
    single { createHttpClient() } withOptions {
        onClose { it?.close() }
    }
    single<PreferencesHelper> { PreferencesHelperImpl() }
    single<SynapseCoreClient> {
        SynapseCoreClientImpl(
            httpClient = get(),
            baseUrlProvider = { get<PreferencesHelper>().apiBaseUrl },
            tokenProvider = { get<PreferencesHelper>().apiKey }
        )
    }
    single<SynapseApiClient> { SynapseApiClientImpl(get(), get(), get(), get()) }
}
