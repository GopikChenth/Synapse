package com.arcadelabs.synapse.core.di

import com.arcadelabs.synapse.core.network.createHttpClient
import com.arcadelabs.synapse.core.network.SyncthingApiClient
import com.arcadelabs.synapse.core.network.SyncthingApiClientImpl
import com.arcadelabs.synapse.core.prefs.PreferencesHelper
import com.arcadelabs.synapse.core.prefs.PreferencesHelperImpl
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

/**
 * Dependency injection module for the core module.
 * Provides the HTTP client, platform preferences helper, and the Syncthing API client.
 */
val coreDiModule = module {
    single { createHttpClient() } withOptions {
        onClose { it?.close() }
    }
    single<PreferencesHelper> { PreferencesHelperImpl() }
    single<SyncthingApiClient> { SyncthingApiClientImpl(get(), get(), get()) }
}
