package com.arcadelabs.synapse.core.di

import com.arcadelabs.synapse.core.network.HttpClientProvider
import com.arcadelabs.synapse.core.network.SyncthingApiClient
import org.koin.dsl.module

val coreDiModule = module {
    single { HttpClientProvider.create() }
    single { SyncthingApiClient(get(), getOrNull<Any>()) }
}
